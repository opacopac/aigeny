package com.tschanz.aigeny.integration.support;

import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generic canned-response mock {@link Tool}, reusable as a base for tool-family-specific mocks
 * (Jira, Bitbucket, ...) in integration tests. Records every call's raw JSON arguments and
 * returns a single, pre-configured text response.
 */
public class GenericMockTool implements Tool {

    private final String name;
    private final String description;
    private String cannedResponse = "";
    private boolean requiresConfirmation = false;
    private final List<String> receivedArguments = new ArrayList<>();

    public GenericMockTool(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** Sets the text returned by every subsequent {@link #execute(String)} call. */
    public GenericMockTool respondWith(String response) {
        this.cannedResponse = response;
        return this;
    }

    /** Marks this tool as requiring user confirmation (write tools), like real write tools do. */
    public GenericMockTool requiringConfirmation(boolean value) {
        this.requiresConfirmation = value;
        return this;
    }

    @Override public String getName()               { return name; }
    @Override public String getDescription()         { return description; }
    @Override public boolean requiresConfirmation()  { return requiresConfirmation; }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(name, description, Map.of("type", "object", "properties", Map.of()));
    }

    @Override
    public ToolResult execute(String argumentsJson) {
        receivedArguments.add(argumentsJson);
        return new ToolResult(cannedResponse);
    }

    public int getCallCount()                  { return receivedArguments.size(); }
    public List<String> getReceivedArguments() { return receivedArguments; }
    public String getLastArguments() {
        return receivedArguments.isEmpty() ? null : receivedArguments.get(receivedArguments.size() - 1);
    }
}

