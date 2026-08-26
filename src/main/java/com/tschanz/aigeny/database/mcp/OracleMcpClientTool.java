package com.tschanz.aigeny.database.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.ToolResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * MCP-based implementation of the {@code query_oracle_db} tool.
 *
 * <p>Instead of talking JDBC directly (like {@link com.tschanz.aigeny.database.OracleDbTool}),
 * this tool launches {@link OracleMcpServerLauncher} as a child process on startup and
 * communicates with it over the MCP stdio transport, exactly like a real external MCP
 * server would be used. This is enabled by setting {@code aigeny.db.mcp-enabled=true}.
 *
 * <p>Activation is mutually exclusive with {@link com.tschanz.aigeny.database.OracleDbTool}
 * (see its {@code @ConditionalOnProperty}), so exactly one {@code query_oracle_db} tool bean
 * is ever registered with {@link com.tschanz.aigeny.orchestration.ToolExecutor}.
 *
 * <p>Because the tool name, description and JSON schema are identical to the direct
 * implementation, this switch is fully transparent to the LLM and to the rest of the
 * orchestration layer.
 */
@Service
@ConditionalOnProperty(name = "aigeny.db.mcp-enabled", havingValue = "true")
public class OracleMcpClientTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(OracleMcpClientTool.class);

    private static final String MSG_NOT_CONFIGURED = "db.error.not_configured";

    private final DbConfiguration dbConfig;
    private final ConfigurationValidator configValidator;

    private volatile McpSyncClient client;

    public OracleMcpClientTool(DbConfiguration dbConfig, ConfigurationValidator configValidator,
                               ObjectMapper objectMapper) {
        super(objectMapper);
        this.dbConfig = dbConfig;
        this.configValidator = configValidator;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    void start() {
        if (!configValidator.isDbConfigured(dbConfig)) {
            log.info("Oracle DB not configured – MCP DB server not started.");
            return;
        }
        try {
            String javaBin = ProcessHandle.current().info().command().orElse("java");

            ServerParameters params = ServerParameters.builder(javaBin)
                    .args(buildJavaArgs())
                    .addEnvVar("AIGENY_DB_URL", nullToEmpty(dbConfig.getUrl()))
                    .addEnvVar("AIGENY_DB_USERNAME", nullToEmpty(dbConfig.getUsername()))
                    .addEnvVar("AIGENY_DB_PASSWORD", nullToEmpty(dbConfig.getPassword()))
                    .addEnvVar("AIGENY_DB_SCHEMA", nullToEmpty(dbConfig.getEffectiveSchema()))
                    .build();

            StdioClientTransport transport = new StdioClientTransport(params, objectMapper);
            McpSyncClient newClient = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("aigeny", "1.0.0"))
                    .build();
            newClient.initialize();
            this.client = newClient;
            log.info("Oracle DB MCP server started (stdio subprocess) and initialized.");
        } catch (Exception e) {
            log.error("Failed to start Oracle DB MCP server: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    void stop() {
        McpSyncClient c = this.client;
        if (c != null) {
            c.closeGracefully();
            log.info("Oracle DB MCP server stopped.");
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * Builds the {@code java ...} arguments used to launch {@link OracleMcpServerLauncher}
     * as a child process.
     *
     * <p>Two scenarios need to be handled:
     * <ul>
     *   <li><b>IDE / {@code mvn spring-boot:run}</b>: {@code java.class.path} is a normal,
     *       {@code File.pathSeparator}-delimited list of classes/jars - a plain
     *       {@code -cp <classpath> <MainClass>} works.</li>
     *   <li><b>Packaged executable Spring Boot fat JAR</b> (e.g. {@code aigeny-1.0.0.jar}):
     *       {@code java.class.path} is just that single jar. Its dependencies live nested
     *       under {@code BOOT-INF/lib/} and are only visible through Spring Boot's own
     *       launcher class loader - a plain {@code -cp} does <em>not</em> see them. In this
     *       case we re-use the same jar but let Spring Boot's {@code PropertiesLauncher}
     *       set up the real classpath, redirecting its entry point to our MCP server main
     *       class via the {@code loader.main} system property.</li>
     * </ul>
     */
    private String[] buildJavaArgs() {
        String classpath = System.getProperty("java.class.path");
        boolean fatJar = classpath != null
                && !classpath.contains(File.pathSeparator)
                && classpath.endsWith(".jar")
                && getClass().getClassLoader().getClass().getName().startsWith("org.springframework.boot.loader");

        if (fatJar) {
            return new String[] {
                    "-Dloader.main=" + OracleMcpServerLauncher.class.getName(),
                    "-cp", classpath,
                    "org.springframework.boot.loader.launch.PropertiesLauncher"
            };
        }
        return new String[] { "-cp", classpath, OracleMcpServerLauncher.class.getName() };
    }

    // ── Tool contract (identical to OracleDbTool) ───────────────────────────────

    @Override public String getName() { return "query_oracle_db"; }

    @Override
    public String getDescription() {
        return "Execute a read-only SELECT query against the Oracle database and return the results. " +
               "You can also use this tool to discover the schema: query all_tables, all_columns, " +
               "or user_tables / user_tab_columns. Always use fully qualified table names (SCHEMA.TABLE).";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> props = Map.of(
            "sql", Map.of("type", "string", "description",
                "A valid Oracle SQL SELECT statement. Only SELECT is allowed."),
            "description", Map.of("type", "string", "description",
                "Brief explanation of what this query retrieves")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", props, "required", List.of("sql", "description")));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        McpSyncClient c = this.client;
        if (c == null) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        JsonNode args = objectMapper.readTree(argumentsJson);
        String sql = args.path("sql").asText("").trim();
        String description = args.path("description").asText("Execute query");

        log.info("  DB REQUEST (MCP) desc=\"{}\"", description);
        log.info("  SQL: {}", sql);

        long t0 = System.currentTimeMillis();
        McpSchema.CallToolResult result = c.callTool(
                new McpSchema.CallToolRequest(getName(), Map.of("sql", sql, "description", description)));
        long elapsed = System.currentTimeMillis() - t0;

        List<McpSchema.Content> content = result.content();
        String text = content.isEmpty() ? "" : asText(content.get(0));

        if (Boolean.TRUE.equals(result.isError())) {
            log.error("  DB REQUEST (MCP) FAILED elapsed={}ms error=\"{}\"", elapsed, text);
            return new ToolResult(text);
        }

        QueryResult qr = tryParseStructuredResult(content);
        log.info("  DB RESPONSE (MCP) elapsed={}ms rows={}", elapsed, qr == null ? 0 : qr.getRows().size());
        return qr != null ? new ToolResult(text, qr) : new ToolResult(text);
    }

    /** Parses the second content block (JSON columns/rows), added by the server for CSV export. */
    @SuppressWarnings("unchecked")
    private QueryResult tryParseStructuredResult(List<McpSchema.Content> content) {
        if (content.size() < 2) return null;
        try {
            JsonNode structured = objectMapper.readTree(asText(content.get(1)));
            List<String> columns = objectMapper.convertValue(structured.get("columns"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<Map<String, Object>> rows = objectMapper.convertValue(structured.get("rows"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return new QueryResult("Oracle DB", columns, rows);
        } catch (Exception e) {
            log.warn("Could not parse structured MCP query result: {}", e.getMessage());
            return null;
        }
    }

    private static String asText(McpSchema.Content c) {
        return (c instanceof McpSchema.TextContent tc) ? tc.text() : "";
    }
}




