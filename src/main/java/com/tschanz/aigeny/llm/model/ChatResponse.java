package com.tschanz.aigeny.llm.model;

import java.util.List;

/**
 * Parsed response from the LLM: either a text message or tool call(s), or both.
 * Claude (and some OpenAI-compatible models) can return text alongside tool calls
 * as an "intermediate" thought before the tools are executed.
 */
public class ChatResponse {

    private String content;           // null if tool calls are present (no accompanying text)
    private List<ToolCall> toolCalls; // null if plain text response
    private String intermediateText;  // text that accompanies tool calls (shown to user before tool execution)

    public ChatResponse(String content) {
        this.content = content;
    }

    public ChatResponse(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public ChatResponse(List<ToolCall> toolCalls, String intermediateText) {
        this.toolCalls = toolCalls;
        this.intermediateText = intermediateText;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasIntermediateText() {
        return intermediateText != null && !intermediateText.isBlank();
    }

    public String getContent()           { return content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getIntermediateText()  { return intermediateText; }
}

