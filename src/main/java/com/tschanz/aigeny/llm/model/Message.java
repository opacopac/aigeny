package com.tschanz.aigeny.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents a single chat message (system / user / assistant / tool).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private String role;
    private String content;
    private List<ToolCall> toolCalls;   // assistant → tool calls
    private String toolCallId;          // tool role: which call this responds to
    private String name;                // tool role: tool name

    public Message() {}

    public static Message system(String content) {
        Message m = new Message(); m.role = "system"; m.content = content; return m;
    }
    public static Message user(String content) {
        Message m = new Message(); m.role = "user"; m.content = content; return m;
    }
    public static Message assistant(String content) {
        Message m = new Message(); m.role = "assistant"; m.content = content; return m;
    }
    public static Message assistantWithToolCalls(List<ToolCall> calls) {
        Message m = new Message(); m.role = "assistant"; m.toolCalls = calls; return m;
    }
    public static Message tool(String toolCallId, String name, String content) {
        Message m = new Message(); m.role = "tool"; m.toolCallId = toolCallId;
        m.name = name; m.content = content; return m;
    }

    // ── Jackson / OpenAI field names ─────────────────────────────────────────

    public String getRole()     { return role; }
    public void   setRole(String r) { this.role = r; }

    public String getContent()  { return content; }
    public void   setContent(String c) { this.content = c; }

    @com.fasterxml.jackson.annotation.JsonProperty("tool_calls")
    public List<ToolCall> getToolCalls() { return toolCalls; }
    @com.fasterxml.jackson.annotation.JsonProperty("tool_calls")
    public void setToolCalls(List<ToolCall> tc) { this.toolCalls = tc; }

    @com.fasterxml.jackson.annotation.JsonProperty("tool_call_id")
    public String getToolCallId() { return toolCallId; }
    @com.fasterxml.jackson.annotation.JsonProperty("tool_call_id")
    public void setToolCallId(String id) { this.toolCallId = id; }

    public String getName() { return name; }
    public void   setName(String n) { this.name = n; }
}

