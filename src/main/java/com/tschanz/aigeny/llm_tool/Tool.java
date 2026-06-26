package com.tschanz.aigeny.llm_tool;

import com.tschanz.aigeny.llm.model.ToolDefinition;

/**
 * Interface for all tools that the LLM can call.
 *
 * <p>Concrete implementations should extend {@link AbstractTool} to inherit the shared
 * {@link com.fasterxml.jackson.databind.ObjectMapper} and the default
 * {@link #getCallDescription(String)} behaviour.
 */
public interface Tool {

    /** Unique name used in tool definitions (must match what LLM calls) */
    String getName();

    /** Human-readable description for the LLM */
    String getDescription();

    /** JSON Schema parameters definition */
    ToolDefinition getDefinition();

    /**
     * Returns true if this tool requires user confirmation before executing.
     * Write tools that modify external state should override this to return true.
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * Execute the tool with the given JSON arguments string.
     * @return result as a string (may be text, JSON, or QueryResult.toText())
     */
    ToolResult execute(String argumentsJson) throws Exception;

    /**
     * Returns a short human-readable description of what this specific call is doing,
     * based on its arguments. Shown in the UI typing indicator.
     * The richer implementation in {@link AbstractTool} reads a {@code description} field
     * from the args JSON. Tools that extend {@link AbstractTool} inherit that behaviour.
     * Tools with structured args (action, query, etc.) should override this.
     */
    default String getCallDescription(String argumentsJson) {
        return getName();
    }
}

