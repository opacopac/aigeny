package com.tschanz.aigeny.integration.support;

import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base mock {@link Tool} for integration tests, reusable across tool families (Jira, Bitbucket,
 * database, ...). Handles the boilerplate every mock tool needs - name/description, optional
 * "requires confirmation" flag, and call recording - and defaults {@link #execute(String)} to a
 * single pre-configured canned text response.
 *
 * <p>Subclasses that need argument-dependent behaviour (e.g. {@link MockDatabaseTool} filtering
 * a table list by a {@code prefix} argument) should call {@link #recordCall(String)} and override
 * {@link #execute(String)} (and {@link #getDefinition()} if a richer schema is useful) instead of
 * relying on the canned response.
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
    public ToolResult execute(String argumentsJson) throws Exception {
        recordCall(argumentsJson);
        return new ToolResult(cannedResponse);
    }

    /** Records a call's raw JSON arguments; subclasses overriding {@link #execute(String)} must call this. */
    protected final void recordCall(String argumentsJson) {
        receivedArguments.add(argumentsJson);
    }

    public int getCallCount()                  { return receivedArguments.size(); }
    public List<String> getReceivedArguments() { return receivedArguments; }
    public String getLastArguments() {
        return receivedArguments.isEmpty() ? null : receivedArguments.get(receivedArguments.size() - 1);
    }
}

