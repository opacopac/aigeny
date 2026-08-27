package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;

/**
 * {@code describe_table}: describes a table's columns (name, type, nullable, primary key)
 * and its foreign keys referencing other tables.
 */
final class DescribeTableHandler implements OracleMcpToolHandler {

    static final String NAME = "describe_table";

    private static final String DESCRIPTION =
            "Describe the columns of a table: name, data type, nullable, primary key flag, and foreign keys " +
            "referencing other tables. Use this before writing a query against an unfamiliar table.";

    private static final String SCHEMA_JSON = """
            {
              "type": "object",
              "properties": {
                "table": {"type": "string", "description": "Table name to describe, optionally schema-qualified (SCHEMA.TABLE)."}
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
        if (ds == null) {
            return OracleSqlSupport.errorResult("ERROR: Could not connect to Oracle database.");
        }

        String owner;
        String tableName;
        int dot = table.indexOf('.');
        if (dot >= 0) {
            owner = table.substring(0, dot).toUpperCase();
            tableName = table.substring(dot + 1).toUpperCase();
        } else {
            owner = null;
            tableName = table.toUpperCase();
        }

        try (Connection conn = ds.getConnection()) {
            String effectiveOwner = owner != null ? owner : OracleSqlSupport.currentSchema(conn);

            List<Map<String, Object>> columns = OracleSqlSupport.queryList(conn,
                    "SELECT column_name, data_type, data_length, data_precision, data_scale, nullable " +
                    "FROM all_tab_columns WHERE owner = ? AND table_name = ? ORDER BY column_id",
                    List.of(effectiveOwner, tableName));

            if (columns.isEmpty()) {
                return OracleSqlSupport.errorResult("ERROR: table not found or no columns visible: " + table);
            }

            Set<String> pkColumns = new LinkedHashSet<>();
            for (Map<String, Object> row : OracleSqlSupport.queryList(conn,
                    "SELECT acc.column_name AS COLUMN_NAME FROM all_constraints ac " +
                    "JOIN all_cons_columns acc ON ac.constraint_name = acc.constraint_name AND ac.owner = acc.owner " +
                    "WHERE ac.owner = ? AND ac.table_name = ? AND ac.constraint_type = 'P'",
                    List.of(effectiveOwner, tableName))) {
                pkColumns.add(String.valueOf(row.get("COLUMN_NAME")));
            }

            List<Map<String, Object>> foreignKeys = OracleSqlSupport.queryList(conn,
                    "SELECT acc.column_name AS FK_COLUMN, r_ac.table_name AS REF_TABLE, r_acc.column_name AS REF_COLUMN " +
                    "FROM all_constraints ac " +
                    "JOIN all_cons_columns acc ON ac.constraint_name = acc.constraint_name AND ac.owner = acc.owner " +
                    "JOIN all_constraints r_ac ON ac.r_constraint_name = r_ac.constraint_name AND ac.r_owner = r_ac.owner " +
                    "JOIN all_cons_columns r_acc ON r_ac.constraint_name = r_acc.constraint_name " +
                    "  AND r_ac.owner = r_acc.owner AND acc.position = r_acc.position " +
                    "WHERE ac.owner = ? AND ac.table_name = ? AND ac.constraint_type = 'R' " +
                    "ORDER BY acc.column_name",
                    List.of(effectiveOwner, tableName));

            List<String> outColumns = List.of("COLUMN_NAME", "DATA_TYPE", "NULLABLE", "PK");
            List<Map<String, Object>> outRows = new ArrayList<>();
            for (Map<String, Object> col : columns) {
                String name = String.valueOf(col.get("COLUMN_NAME"));
                boolean nullable = "Y".equalsIgnoreCase(String.valueOf(col.get("NULLABLE")));
                boolean pk = pkColumns.contains(name);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("COLUMN_NAME", name);
                row.put("DATA_TYPE", OracleSqlSupport.formatDataType(col));
                row.put("NULLABLE", nullable);
                row.put("PK", pk);
                outRows.add(row);
            }

            StringBuilder text = new StringBuilder(OracleSqlSupport.toText(outColumns, outRows));
            if (foreignKeys.isEmpty()) {
                text.append("\n(no foreign keys)");
            } else {
                text.append("\nForeign keys:\n");
                for (Map<String, Object> fk : foreignKeys) {
                    text.append("  ").append(fk.get("FK_COLUMN")).append(" -> ")
                        .append(fk.get("REF_TABLE")).append(".").append(fk.get("REF_COLUMN")).append("\n");
                }
            }

            String structuredJson = mapper.writeValueAsString(
                    Map.of("columns", outColumns, "rows", outRows, "foreignKeys", foreignKeys));
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(text.toString()), new McpSchema.TextContent(structuredJson)),
                    false);
        } catch (Exception e) {
            return OracleSqlSupport.errorResult("ERROR: " + e.getMessage());
        }
    }
}

