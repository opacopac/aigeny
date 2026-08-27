package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.database.mcp_client.OracleMcpConnection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Standalone entry point for the embedded Oracle DB MCP server.
 *
 * <p>This is <b>Phase 1</b> of migrating AIgeny's tool connections to MCP: the
 * server is not a separately deployed/maintained project - it lives in the same
 * codebase and JAR as the rest of AIgeny and is spawned as a child process
 * (stdio transport) by {@link OracleMcpConnection} on application startup.
 *
 * <p>Deliberately framework-free (no Spring context): it only needs DB
 * credentials, which are passed via environment variables by the parent
 * process ({@code AIGENY_DB_URL}, {@code AIGENY_DB_USERNAME},
 * {@code AIGENY_DB_PASSWORD}, {@code AIGENY_DB_SCHEMA}). This keeps the
 * subprocess lightweight and fast to start.
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
        String url      = System.getenv("AIGENY_DB_URL");
        String username = System.getenv("AIGENY_DB_USERNAME");
        String password = System.getenv("AIGENY_DB_PASSWORD");
        String schema   = System.getenv("AIGENY_DB_SCHEMA");

        HikariDataSource dataSource = buildDataSource(url, username, password, schema);
        ObjectMapper objectMapper = new ObjectMapper();

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(objectMapper);

        McpServer.SyncSpecification builder = McpServer.sync(transportProvider)
                .serverInfo("aigeny-oracle-db", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build());

        for (OracleMcpToolHandler handler : HANDLERS) {
            builder = builder.tool(new McpSchema.Tool(handler.name(), handler.description(), handler.schemaJson()),
                    (exchange, arguments) -> handler.handle(dataSource, objectMapper, arguments));
        }

        McpSyncServer server = builder.build();

        // Nothing else to do on the main thread: StdioServerTransportProvider reads
        // stdin / writes stdout on its own scheduler. Keep the JVM alive until the
        // parent process kills us (on AIgeny shutdown or tool re-configuration).
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        new CountDownLatch(1).await();
    }

    private static HikariDataSource buildDataSource(String url, String username, String password, String schema) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(username);
        hc.setPassword(password);
        hc.setMaximumPoolSize(3);
        hc.setConnectionTimeout(15_000);
        hc.setReadOnly(true);
        hc.setPoolName("AIgeny-Oracle-MCP");
        if (schema != null && !schema.isBlank() && !schema.equalsIgnoreCase(username)) {
            hc.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + schema);
        }
        return new HikariDataSource(hc);
    }
}

