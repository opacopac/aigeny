package com.tschanz.aigeny.llm_tool;

import com.tschanz.aigeny.llm.model.ToolDefinition;

/**
 * Interface for all tools that the LLM can call.
 */
public interface Tool {

    /** Unique name used in tool definitions (must match what LLM calls) */
    String getName();

    /** Human-readable description for the LLM */
    String getDescription();

    /** JSON Schema parameters definition */
    ToolDefinition getDefinition();

    /**
     * Execute the tool with the given JSON arguments string.
     * @return result as a string (may be text, JSON, or QueryResult.toText())
     */
    ToolResult execute(String argumentsJson) throws Exception;
}

