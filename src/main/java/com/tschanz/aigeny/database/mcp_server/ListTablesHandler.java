package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * {@code list_tables}: lists all tables in the current database schema, optionally
 * filtered by a case-insensitive name prefix.
 */
final class ListTablesHandler implements OracleMcpToolHandler {

    static final String NAME = "list_tables";

    private static final String DESCRIPTION =
            "List all tables in the current database schema. Optionally filter by a case-insensitive name prefix. " +
            "Use this first to discover which tables exist.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "prefix": {"type": "string", "description": "Optional case-insensitive prefix filter for table names."}
              },
              "required": []
            }
            """;

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }
    @Override public String schemaJson() { return SCHEMA_JSON; }

    @Override
    public McpSchema.CallToolResult handle(DataSource ds, ObjectMapper mapper, Map<String, Object> arguments) {
        String prefix = OracleSqlSupport.stringArg(arguments, "prefix");

        StringBuilder sql = new StringBuilder(
                "SELECT table_name FROM all_tables WHERE owner = SYS_CONTEXT('USERENV','CURRENT_SCHEMA')");
        List<Object> binds = new ArrayList<>();
        if (prefix != null && !prefix.isBlank()) {
            sql.append(" AND UPPER(table_name) LIKE UPPER(?) || '%'");
            binds.add(prefix);
        }
        sql.append(" ORDER BY table_name");

        return OracleSqlSupport.runSelect(ds, mapper, sql.toString(), binds, OracleSqlSupport.MAX_ROWS);
    }
}

