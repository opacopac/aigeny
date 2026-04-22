package com.tschanz.aigeny.llm.model;

import java.util.List;

/**
 * Parsed response from the LLM: either a text message or tool call(s).
 */
public class ChatResponse {

    private String content;          // null if tool calls are present
    private List<ToolCall> toolCalls; // null if plain text response

    public ChatResponse(String content) {
        this.content = content;
    }

    public ChatResponse(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public String getContent()          { return content; }
    public List<ToolCall> getToolCalls(){ return toolCalls; }
}

