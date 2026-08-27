package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * {@code sample_table}: returns up to N sample rows from a table.
 */
final class SampleTableHandler implements OracleMcpToolHandler {

    static final String NAME = "sample_table";

    private static final int DEFAULT_LIMIT = 20;

    private static final String DESCRIPTION =
            "Return up to N sample rows from a table, useful to inspect actual data / value formats before " +
            "writing a query against it.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "table": {"type": "string", "description": "Table name to sample, optionally schema-qualified (SCHEMA.TABLE)."},
                "limit": {"type": "integer", "description": "Max rows to return (default 20, max 5000)."}
              },
              "required": ["table"]
            }
            """;

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }
    @Override public String schemaJson() { return SCHEMA_JSON; }

    @Override
    public McpSchema.CallToolResult handle(DataSource ds, ObjectMapper mapper, Map<String, Object> arguments) {
        String table = OracleSqlSupport.stringArg(arguments, "table");
        if (table == null || table.isBlank()) {
            return OracleSqlSupport.errorResult("ERROR: 'table' argument is required.");
        }
        if (!OracleSqlSupport.VALID_IDENTIFIER.matcher(table).matches()) {
            return OracleSqlSupport.errorResult("ERROR: invalid table name '" + table + "'.");
        }
        int limit = OracleSqlSupport.clamp(
                OracleSqlSupport.intArg(arguments, "limit", DEFAULT_LIMIT), 1, OracleSqlSupport.MAX_ROWS);

        String sql = "SELECT * FROM " + table + " FETCH FIRST ? ROWS ONLY";
        return OracleSqlSupport.runSelect(ds, mapper, sql, List.of(limit), limit);
    }
}

