package com.tschanz.aigeny.database.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;

/**
 * Standalone entry point for the embedded Oracle DB MCP server.
 *
 * <p>This is <b>Phase 1</b> of migrating AIgeny's tool connections to MCP: the
 * server is not a separately deployed/maintained project - it lives in the same
 * codebase and JAR as the rest of AIgeny and is spawned as a child process
 * (stdio transport) by {@link OracleMcpClientTool} when
 * {@code aigeny.db.mcp-enabled=true}.
 *
 * <p>Deliberately framework-free (no Spring context): it only needs DB
 * credentials, which are passed via environment variables by the parent
 * process ({@code AIGENY_DB_URL}, {@code AIGENY_DB_USERNAME},
 * {@code AIGENY_DB_PASSWORD}, {@code AIGENY_DB_SCHEMA}). This keeps the
 * subprocess lightweight and fast to start.
 *
 * <p><b>Phase 2 outlook:</b> once this proves out, the same class (or a copy of
 * it) can be extracted into its own Maven module/artifact and run as a truly
 * independent process (or container). {@link OracleMcpClientTool} would then
 * simply point its {@code ServerParameters}/transport at that external process
 * instead of launching a subprocess of the current JVM - no change is needed
 * to the {@code query_oracle_db} tool contract seen by the LLM.
 */
public final class OracleMcpServerLauncher {

    private static final String TOOL_NAME = "query_oracle_db";
    private static final int MAX_ROWS = 5000;

    private static final Pattern SAFE_SQL = Pattern.compile(
            "^\\s*SELECT\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|EXEC|EXECUTE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String TOOL_SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "sql": {"type": "string", "description": "A valid Oracle SQL SELECT statement. Only SELECT is allowed."},
                "description": {"type": "string", "description": "Brief explanation of what this query retrieves"}
              },
              "required": ["sql", "description"]
            }
            """;

    private OracleMcpServerLauncher() {}

    public static void main(String[] args) throws Exception {
        String url      = System.getenv("AIGENY_DB_URL");
        String username = System.getenv("AIGENY_DB_USERNAME");
        String password = System.getenv("AIGENY_DB_PASSWORD");
        String schema   = System.getenv("AIGENY_DB_SCHEMA");

        HikariDataSource dataSource = buildDataSource(url, username, password, schema);
        ObjectMapper objectMapper = new ObjectMapper();

        McpSchema.Tool toolDefinition = new McpSchema.Tool(
                TOOL_NAME,
                "Execute a read-only SELECT query against the Oracle database and return the results. " +
                "You can also use this tool to discover the schema: query all_tables, all_columns, " +
                "or user_tables / user_tab_columns. Always use fully qualified table names (SCHEMA.TABLE).",
                TOOL_SCHEMA_JSON
        );

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(objectMapper);

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("aigeny-oracle-db", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tool(toolDefinition, (exchange, arguments) -> handleQuery(dataSource, objectMapper, arguments))
                .build();

        // Nothing else to do on the main thread: StdioServerTransportProvider reads
        // stdin / writes stdout on its own scheduler. Keep the JVM alive until the
        // parent process kills us (on AIgeny shutdown or tool re-configuration).
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        new CountDownLatch(1).await();
    }

    private static McpSchema.CallToolResult handleQuery(HikariDataSource ds, ObjectMapper mapper,
                                                          Map<String, Object> arguments) {
        String sql = String.valueOf(arguments.getOrDefault("sql", "")).trim();

        if (!SAFE_SQL.matcher(sql).matches()) {
            return new McpSchema.CallToolResult("ERROR: Only SELECT queries are allowed.", true);
        }
        if (DANGEROUS.matcher(sql).find()) {
            return new McpSchema.CallToolResult("ERROR: Potentially dangerous SQL keywords detected. Query rejected.", true);
        }
        if (ds == null) {
            return new McpSchema.CallToolResult("ERROR: Could not connect to Oracle database.", true);
        }

        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            stmt.setMaxRows(MAX_ROWS);
            stmt.setFetchSize(200);

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnLabel(i));

                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) row.put(columns.get(i - 1), rs.getObject(i));
                    rows.add(row);
                }

                String text = toText(columns, rows);
                // Second content block: machine-readable columns/rows so the client
                // can rebuild a QueryResult for CSV export, in addition to the
                // human-readable text block the LLM reads.
                String structuredJson = mapper.writeValueAsString(Map.of("columns", columns, "rows", rows));

                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(text), new McpSchema.TextContent(structuredJson)),
                        false);
            }
        } catch (Exception e) {
            return new McpSchema.CallToolResult("SQL ERROR: " + e.getMessage(), true);
        }
    }

    private static String toText(List<String> columns, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return "(no rows returned)";
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(" | ", columns)).append("\n");
        sb.append("-".repeat(60)).append("\n");
        int shown = Math.min(rows.size(), 200);
        for (int i = 0; i < shown; i++) {
            Map<String, Object> row = rows.get(i);
            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) sb.append(" | ");
                Object val = row.get(columns.get(j));
                sb.append(val == null ? "NULL" : val.toString());
            }
            sb.append("\n");
        }
        if (rows.size() > 200) {
            sb.append("... (").append(rows.size() - 200).append(" more rows - export to see all)");
        }
        return sb.toString();
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

