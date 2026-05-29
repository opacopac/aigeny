package com.tschanz.aigeny.llm_tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.ToolDefinition;

/**
 * Interface for all tools that the LLM can call.
 */
public interface Tool {

    ObjectMapper _TOOL_JSON = new ObjectMapper();

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

    /**
     * Returns a short human-readable description of what this specific call is doing,
     * based on its arguments. Shown in the UI typing indicator.
     * Default: tries a 'description' field in the args, falls back to the tool name.
     * Tools with structured args (action, query, etc.) should override this.
     */
    default String getCallDescription(String argumentsJson) {
        try {
            JsonNode node = _TOOL_JSON.readTree(argumentsJson);
            JsonNode desc = node.get("description");
            if (desc != null && !desc.isNull() && !desc.asText().isBlank()) {
                return desc.asText();
            }
        } catch (Exception ignored) {}
        return getName();
    }
}

