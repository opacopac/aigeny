package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.llm_tool.ToolResult;

/**
 * Return value from OrchestrationService.chat():
 * the assistant response text plus any tabular data available for export.
 */
public record ChatResult(String response, ToolResult lastToolResult) {
    public boolean hasExportData() {
        return lastToolResult != null && lastToolResult.hasQueryResult();
    }
}

