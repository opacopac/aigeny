package com.tschanz.aigeny.llm_tool;

import java.util.List;
import java.util.Map;

/**
 * Holds the result of a database or Jira query: column headers + rows.
 */
public class QueryResult {

    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final String sourceName; // "Oracle DB" or "Jira"

    public QueryResult(String sourceName, List<String> columns, List<Map<String, Object>> rows) {
        this.sourceName = sourceName;
        this.columns = columns;
        this.rows = rows;
    }

    public String getSourceName()              { return sourceName; }
    public List<String> getColumns()           { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }

    public boolean isEmpty() { return rows == null || rows.isEmpty(); }

    /** Simple text representation for the LLM */
    public String toText() {
        if (isEmpty()) return "(no rows returned)";
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

