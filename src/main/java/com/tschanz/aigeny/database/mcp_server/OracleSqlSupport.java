package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Shared SQL execution / formatting helpers used by all {@link OracleMcpToolHandler}
 * implementations. Kept as static utilities (no state) so each handler class only needs
 * to contain its own tool-specific SQL and argument handling.
 */
final class OracleSqlSupport {

    static final int MAX_ROWS = 5000;

    /** Table identifier: plain name or SCHEMA.TABLE, no other characters allowed (prevents SQL injection). */
    static final Pattern VALID_IDENTIFIER = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_$#]*(\\.[A-Za-z_][A-Za-z0-9_$#]*)?$");

    private OracleSqlSupport() {}

    /** Runs a SELECT and builds the standard two-content-block {@code CallToolResult} (text + structured JSON). */
    static McpSchema.CallToolResult runSelect(DataSource ds, ObjectMapper mapper, String sql,
                                               List<Object> binds, int maxRows) {
        if (ds == null) {
            return errorResult("ERROR: Could not connect to Oracle database.");
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            stmt.setMaxRows(maxRows);
            stmt.setFetchSize(Math.min(200, maxRows));
            for (int i = 0; i < binds.size(); i++) {
                stmt.setObject(i + 1, binds.get(i));
            }

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
            return errorResult("SQL ERROR: " + e.getMessage());
        }
    }

    /** Runs a query and returns its rows as plain maps, for internal dictionary lookups (not exposed as a CallToolResult). */
    static List<Map<String, Object>> queryList(Connection conn, String sql, List<Object> binds) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < binds.size(); i++) stmt.setObject(i + 1, binds.get(i));
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
                return rows;
            }
        }
    }

    static String currentSchema(Connection conn) throws Exception {
        List<Map<String, Object>> rows = queryList(conn,
                "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AS SCHEMA_NAME FROM DUAL", List.of());
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("SCHEMA_NAME"));
    }

    static String formatDataType(Map<String, Object> col) {
        String type = String.valueOf(col.get("DATA_TYPE"));
        Object length = col.get("DATA_LENGTH");
        Object precision = col.get("DATA_PRECISION");
        Object scale = col.get("DATA_SCALE");
        if (precision != null) {
            return scale != null && !"0".equals(String.valueOf(scale))
                    ? type + "(" + precision + "," + scale + ")"
                    : type + "(" + precision + ")";
        }
        if (length != null && ("VARCHAR2".equalsIgnoreCase(type) || "CHAR".equalsIgnoreCase(type)
                || "NVARCHAR2".equalsIgnoreCase(type) || "RAW".equalsIgnoreCase(type))) {
            return type + "(" + length + ")";
        }
        return type;
    }

    static String stringArg(Map<String, Object> arguments, String key) {
        Object v = arguments.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    static int intArg(Map<String, Object> arguments, String key, int defaultValue) {
        Object v = arguments.get(key);
        if (v == null) return defaultValue;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (Exception e) { return defaultValue; }
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(message, true);
    }

    /** Package-private for unit testing. */
    static String toText(List<String> columns, List<Map<String, Object>> rows) {
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
}

