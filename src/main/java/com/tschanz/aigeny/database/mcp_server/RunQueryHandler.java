package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * {@code run_query}: runs an arbitrary read-only SELECT query with a configurable row limit.
 */
final class RunQueryHandler implements OracleMcpToolHandler {

    static final String NAME = "run_query";

    private static final int DEFAULT_LIMIT = 200;

    private static final Pattern SAFE_SQL = Pattern.compile(
            "^\\s*SELECT\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|EXEC|EXECUTE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String DESCRIPTION =
            "Execute a read-only SELECT query against the Oracle database and return the results, with a " +
            "configurable row limit. Use list_tables/describe_table/search_schema first to discover the schema.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "sql": {"type": "string", "description": "A valid Oracle SQL SELECT statement. Only SELECT is allowed."},
                "description": {"type": "string", "description": "Brief explanation of what this query retrieves"},
                "limit": {"type": "integer", "description": "Max rows to return (default 200, max 5000)."}
              },
              "required": ["sql", "description"]
            }
            """;

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }
    @Override public String schemaJson() { return SCHEMA_JSON; }

    @Override
    public McpSchema.CallToolResult handle(DataSource ds, ObjectMapper mapper, Map<String, Object> arguments) {
        String sql = OracleSqlSupport.stringArg(arguments, "sql");
        sql = sql == null ? "" : sql.trim();
        int limit = OracleSqlSupport.clamp(
                OracleSqlSupport.intArg(arguments, "limit", DEFAULT_LIMIT), 1, OracleSqlSupport.MAX_ROWS);

        if (!SAFE_SQL.matcher(sql).matches()) {
            return OracleSqlSupport.errorResult("ERROR: Only SELECT queries are allowed.");
        }
        if (DANGEROUS.matcher(sql).find()) {
            return OracleSqlSupport.errorResult("ERROR: Potentially dangerous SQL keywords detected. Query rejected.");
        }

        return OracleSqlSupport.runSelect(ds, mapper, sql, List.of(), limit);
    }
}

