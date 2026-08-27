package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

/**
 * {@code search_schema}: searches table and column names for a case-insensitive substring match.
 */
final class SearchSchemaHandler implements OracleMcpToolHandler {

    static final String NAME = "search_schema";

    private static final String DESCRIPTION =
            "Search the schema for tables and columns whose name contains the given term (case-insensitive " +
            "substring match). Useful to discover relevant tables/columns when you don't know the exact name.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "term": {"type": "string", "description": "Search term matched (case-insensitive substring) against table and column names."}
              },
              "required": ["term"]
            }
            """;

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }
    @Override public String schemaJson() { return SCHEMA_JSON; }

    @Override
    public McpSchema.CallToolResult handle(DataSource ds, ObjectMapper mapper, Map<String, Object> arguments) {
        String term = OracleSqlSupport.stringArg(arguments, "term");
        if (term == null || term.isBlank()) {
            return OracleSqlSupport.errorResult("ERROR: 'term' argument is required.");
        }
        String likeTerm = "%" + term + "%";

        String sql =
                "SELECT table_name, CAST(NULL AS VARCHAR2(128)) AS column_name, 'TABLE' AS match_type " +
                "FROM all_tables WHERE owner = SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND UPPER(table_name) LIKE UPPER(?) " +
                "UNION ALL " +
                "SELECT table_name, column_name, 'COLUMN' AS match_type " +
                "FROM all_tab_columns WHERE owner = SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND UPPER(column_name) LIKE UPPER(?) " +
                "ORDER BY 1, 3";

        return OracleSqlSupport.runSelect(ds, mapper, sql, List.of(likeTerm, likeTerm), OracleSqlSupport.MAX_ROWS);
    }
}

