package com.tschanz.aigeny.tools;

/**
 * Result of a tool execution - text for the LLM plus optional tabular data for export.
 */
public class ToolResult {

    private final String text;
    private final QueryResult queryResult; // may be null

    public ToolResult(String text) {
        this.text = text;
        this.queryResult = null;
    }

    public ToolResult(String text, QueryResult queryResult) {
        this.text = text;
        this.queryResult = queryResult;
    }

    public String getText()             { return text; }
    public QueryResult getQueryResult() { return queryResult; }
    public boolean hasQueryResult()     { return queryResult != null && !queryResult.isEmpty(); }
}

