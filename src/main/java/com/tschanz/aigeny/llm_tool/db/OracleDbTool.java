package com.tschanz.aigeny.llm_tool.db;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tool that executes read-only SQL SELECT queries against Oracle DB.
 * The generated SQL is logged and included in the tool result so it appears in the chat.
 */
@Service
public class OracleDbTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(OracleDbTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ROWS = 5000;

    private static final Pattern SAFE_SQL = Pattern.compile(
            "^\\s*SELECT\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|EXEC|EXECUTE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private final AigenyProperties props;
    private HikariDataSource pool;

    public OracleDbTool(AigenyProperties props) {
        this.props = props;
    }

    private synchronized HikariDataSource getPool() {
        if (pool == null && props.isDbConfigured()) {
            try {
                AigenyProperties.Db db = props.getDb();
                HikariConfig hc = new HikariConfig();
                hc.setJdbcUrl(db.getUrl());
                hc.setUsername(db.getUsername());
                hc.setPassword(db.getPassword());
                hc.setMaximumPoolSize(3);
                hc.setConnectionTimeout(15_000);
                hc.setReadOnly(true);
                hc.setPoolName("AIgeny-Oracle");
                pool = new HikariDataSource(hc);
                log.info("Oracle connection pool created");
            } catch (Exception e) {
                log.error("Failed to create Oracle pool: {}", e.getMessage());
            }
        }
        return pool;
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
        if (!props.isDbConfigured()) {
            return new ToolResult(Messages.get(Messages.DB_ERROR_NOT_CONFIGURED));
        }

        JsonNode args = JSON.readTree(argumentsJson);
        String sql = args.path("sql").asText("").trim();
        String description = args.path("description").asText("Execute query");

        if (!SAFE_SQL.matcher(sql).matches()) {
            return new ToolResult(Messages.get(Messages.DB_ERROR_SELECT_ONLY));
        }
        if (DANGEROUS.matcher(sql).find()) {
            return new ToolResult(Messages.get(Messages.DB_ERROR_DANGEROUS_SQL));
        }

        log.info("▶ DB REQUEST  desc=\"{}\"", description);
        log.info("  SQL: {}", sql);

        HikariDataSource ds = getPool();
        if (ds == null) {
            log.error("✗ DB REQUEST  FAILED - connection pool unavailable");
            return new ToolResult(Messages.get(Messages.DB_ERROR_NO_CONNECTION));
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
                log.info("✓ DB RESPONSE rows={} cols={} elapsed={}ms", rows.size(), colCount, elapsed);

                QueryResult qr = new QueryResult("Oracle DB", columns, rows);
                return new ToolResult(qr.toText(), qr);
            }
        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("✗ DB REQUEST  FAILED elapsed={}ms error=\"{}\"", elapsed, e.getMessage());
            return new ToolResult(Messages.get(Messages.DB_ERROR_SQL, e.getMessage()));
        }
    }
}

