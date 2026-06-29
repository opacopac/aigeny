package com.tschanz.aigeny.database;

import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tool that executes read-only SQL SELECT queries against Oracle DB.
 *
 * <p>The connection pool lifecycle is delegated to {@link OracleConnectionPool}
 * (S-4 fix). This class is now solely responsible for SQL validation and query
 * execution.
 *
 * <p>The generated SQL is logged and included in the tool result so it appears
 * in the chat.
 */
@Service
public class OracleDbTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(OracleDbTool.class);
    private static final int MAX_ROWS = 5000;

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED = "db.error.not_configured";
    private static final String MSG_SELECT_ONLY    = "db.error.select_only";
    private static final String MSG_DANGEROUS_SQL  = "db.error.dangerous_sql";
    private static final String MSG_NO_CONNECTION  = "db.error.no_connection";
    private static final String MSG_SQL_ERROR      = "db.error.sql";

    private static final Pattern SAFE_SQL = Pattern.compile(
            "^\\s*SELECT\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|EXEC|EXECUTE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final OracleConnectionPool connectionPool;

    public OracleDbTool(OracleConnectionPool connectionPool, ObjectMapper objectMapper) {
        super(objectMapper);
        this.connectionPool = connectionPool;
    }

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
        if (!connectionPool.isConfigured()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        JsonNode args = objectMapper.readTree(argumentsJson);
        String sql = args.path("sql").asText("").trim();
        String description = args.path("description").asText("Execute query");

        if (!SAFE_SQL.matcher(sql).matches()) {
            return new ToolResult(Messages.get(MSG_SELECT_ONLY));
        }
        if (DANGEROUS.matcher(sql).find()) {
            return new ToolResult(Messages.get(MSG_DANGEROUS_SQL));
        }

        log.info("  DB REQUEST  desc=\"{}\"", description);
        log.info("  SQL: {}", sql);

        DataSource ds = connectionPool.getDataSource();
        if (ds == null) {
            log.error("  DB REQUEST  FAILED - connection pool unavailable");
            return new ToolResult(Messages.get(MSG_NO_CONNECTION));
        }

        long t0 = System.currentTimeMillis();
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

                long elapsed = System.currentTimeMillis() - t0;
                log.info("  DB RESPONSE rows={} cols={} elapsed={}ms", rows.size(), colCount, elapsed);

                QueryResult qr = new QueryResult("Oracle DB", columns, rows);
                return new ToolResult(qr.toText(), qr);
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("  DB REQUEST  FAILED elapsed={}ms error=\"{}\"", elapsed, e.getMessage());
            return new ToolResult(Messages.get(MSG_SQL_ERROR, e.getMessage()));
        }
    }
}
