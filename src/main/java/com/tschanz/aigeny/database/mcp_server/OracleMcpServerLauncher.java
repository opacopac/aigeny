package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.database.mcp_client.OracleMcpConnection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;

/**
 * Standalone entry point for the embedded Oracle DB MCP server.
 *
 * <p>This is <b>Phase 1</b> of migrating AIgeny's tool connections to MCP: the
 * server is not a separately deployed/maintained project - it lives in the same
 * codebase and JAR as the rest of AIgeny and is spawned as a child process
 * (stdio transport) by {@link OracleMcpConnection} on application startup.
 *
 * <p>Deliberately framework-free (no Spring context): it only needs DB credentials, passed via
 * environment variables by the parent process:
 * <ul>
 *   <li>{@code AIGENY_DB_STAGES_JSON} - a JSON object of all configured stages, each with
 *       {@code url}/{@code username}/{@code password}/{@code schema}, e.g.
 *       {@code {"INTE": {"url": "...", "username": "...", ...}, "PROD": {...}}}. Built by
 *       {@link OracleMcpConnection#buildStagesJson()} from {@code aigeny.db.stages} - the set of
 *       stage names is fully open-ended (not hardcoded to INTE/PROD), so any number of
 *       additional stages (e.g. {@code TEST}/{@code DEV}) can be configured with no code change.</li>
 *   <li>{@code AIGENY_DB_DEFAULT_CONTEXT} / {@code AIGENY_DB_DEFAULT_STAGE} - defaults for the
 *       mandatory {@code context}/{@code stage} tool arguments (see {@link ContextStageSupport}).</li>
 * </ul>
 * This keeps the subprocess lightweight and fast to start.
 *
 * <p>Every tool call carries mandatory {@code context}/{@code stage} arguments (see
 * {@link ContextStageSupport}): {@code context} is validated against the single configured
 * value, and {@code stage} selects which of the {@link HikariDataSource}s built here (one per
 * configured stage) is used for that call.
 *
 * <p>Each tool is implemented by its own {@link OracleMcpToolHandler}: its name,
 * description, JSON parameter schema and SQL logic all live together in one class -
 * see {@link ListTablesHandler}, {@link DescribeTableHandler}, {@link SearchSchemaHandler},
 * {@link SampleTableHandler} and {@link RunQueryHandler}. This launcher just wires each
 * handler to the MCP server; shared SQL plumbing lives in {@link OracleSqlSupport}.
 * These handler declarations are the single source of truth - the client side
 * ({@link OracleMcpConnection} / {@code *Tool} classes) discovers them dynamically via
 * {@code listTools()} instead of duplicating this information.
 *
 * <p><b>Phase 2 outlook:</b> once this proves out, the same class (or a copy of
 * it) can be extracted into its own Maven module/artifact and run as a truly
 * independent process (or container). {@link OracleMcpConnection} would then
 * simply point its {@code ServerParameters}/transport at that external process
 * instead of launching a subprocess of the current JVM - no change is needed
 * to the tool contracts seen by the LLM.
 */
public final class OracleMcpServerLauncher {

    private static final List<OracleMcpToolHandler> HANDLERS = List.of(
            new ListTablesHandler(),
            new DescribeTableHandler(),
            new SearchSchemaHandler(),
            new SampleTableHandler(),
            new RunQueryHandler()
    );

    private OracleMcpServerLauncher() {}

    public static void main(String[] args) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        String stagesJson = System.getenv("AIGENY_DB_STAGES_JSON");
        String allowedContext = System.getenv("AIGENY_DB_DEFAULT_CONTEXT");
        String defaultStage = System.getenv("AIGENY_DB_DEFAULT_STAGE");

        // One HikariDataSource per configured stage - the "stage" tool argument (see
        // ContextStageSupport) selects between them at call time. Fully open-ended: any stage
        // name present in AIGENY_DB_STAGES_JSON with a non-blank url gets a pool, no fixed set
        // of stage names (e.g. only INTE/PROD) is hardcoded here.
        Map<String, DataSource> stageDataSources = buildStageDataSources(stagesJson, objectMapper);

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        var builder = McpServer.sync(transportProvider)
                .serverInfo("aigeny-oracle-db", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build());

        for (OracleMcpToolHandler handler : HANDLERS) {
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(handler.name())
                    .description(handler.description())
                    .inputSchema(jsonMapper, handler.schemaJson())
                    .build();
            builder.tool(tool, (exchange, arguments) -> {
                ContextStageSupport.Resolution resolution = ContextStageSupport.resolve(
                        arguments, allowedContext, allowedContext, defaultStage, stageDataSources);
                if (resolution.isError()) {
                    return resolution.error();
                }
                return handler.handle(resolution.dataSource(), objectMapper, arguments);
            });
        }

        McpSyncServer server = builder.build();

        // Nothing else to do on the main thread: StdioServerTransportProvider reads
        // stdin / writes stdout on its own scheduler. Keep the JVM alive until the
        // parent process kills us (on AIgeny shutdown or tool re-configuration).
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        new CountDownLatch(1).await();
    }

    /**
     * Parses {@code AIGENY_DB_STAGES_JSON} (see class javadoc) and builds one
     * {@link HikariDataSource} per stage that has a non-blank {@code url}. Stages with a blank/
     * missing URL are simply omitted - {@link ContextStageSupport} then reports "stage not
     * available" for them. Any parsing failure results in an empty map (same effect: every
     * stage is reported as unavailable) rather than crashing the subprocess.
     */
    private static Map<String, DataSource> buildStageDataSources(String stagesJson, ObjectMapper mapper) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        if (stagesJson == null || stagesJson.isBlank()) {
            return result;
        }
        try {
            com.fasterxml.jackson.databind.type.TypeFactory typeFactory = mapper.getTypeFactory();
            var innerMapType = typeFactory.constructMapType(Map.class, String.class, String.class);
            var outerMapType = typeFactory.constructMapType(Map.class,
                    typeFactory.constructType(String.class), innerMapType);
            Map<String, Map<String, String>> stages = mapper.readValue(stagesJson, outerMapType);
            for (Map.Entry<String, Map<String, String>> entry : stages.entrySet()) {
                Map<String, String> cfg = entry.getValue();
                String stageName = entry.getKey().toUpperCase();
                String url = cfg.get("url");
                if (url == null || url.isBlank()) {
                    continue;
                }
                result.put(stageName,
                        buildDataSource(url, cfg.get("username"), cfg.get("password"), cfg.get("schema"), stageName));
            }
        } catch (Exception e) {
            // Leave result empty - every stage will be reported as "not available" by
            // ContextStageSupport rather than crashing the whole subprocess.
        }
        return result;
    }

    private static HikariDataSource buildDataSource(String url, String username, String password, String schema,
                                                      String stage) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(username);
        hc.setPassword(password);
        hc.setMaximumPoolSize(3);
        hc.setConnectionTimeout(15_000);
        hc.setReadOnly(true);
        hc.setPoolName("AIgeny-Oracle-MCP-" + stage);
        if (schema != null && !schema.isBlank() && !schema.equalsIgnoreCase(username)) {
            hc.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
        }
        return new HikariDataSource(hc);
    }
}


