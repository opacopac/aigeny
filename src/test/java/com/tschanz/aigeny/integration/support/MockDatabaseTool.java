package com.tschanz.aigeny.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mock of a database MCP tool (e.g. the Oracle {@code list_tables} tool normally provided at
 * runtime by {@code OracleMcpToolProvider}/{@code GenericOracleMcpTool}). Lets tests simulate the
 * database MCP server's responses without a real Oracle connection or MCP transport.
 *
 * <p>Currently understands the {@code list_tables} shape (optional {@code prefix} argument,
 * case-insensitive filter over a canned table list) since that's what the initial "main loop"
 * integration test exercises; extend as more DB tool scenarios are needed.
 */
public class MockDatabaseTool implements Tool {

    public static final String LIST_TABLES = "list_tables";

    private final ObjectMapper mapper = new ObjectMapper();
    private final String name;
    private final List<String> tableNames;
    private final List<String> receivedArguments = new ArrayList<>();

    public MockDatabaseTool(String name, List<String> tableNames) {
        this.name = name;
        this.tableNames = tableNames;
    }

    /** Convenience factory: a {@code list_tables} mock backed by the given canned table names. */
    public static MockDatabaseTool listTables(String... tableNames) {
        return new MockDatabaseTool(LIST_TABLES, List.of(tableNames));
    }

    @Override public String getName()        { return name; }
    @Override public String getDescription() { return "Mock database MCP tool: " + name; }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(name, getDescription(), Map.of(
                "type", "object",
                "properties", Map.of("prefix", Map.of("type", "string",
                        "description", "Optional case-insensitive prefix filter for table names."))));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        receivedArguments.add(argumentsJson);

        String prefix = null;
        if (argumentsJson != null && !argumentsJson.isBlank()) {
            JsonNode node = mapper.readTree(argumentsJson);
            JsonNode prefixNode = node.get("prefix");
            if (prefixNode != null && !prefixNode.isNull()) {
                prefix = prefixNode.asText();
            }
        }

        final String finalPrefix = prefix;
        List<String> matching = tableNames.stream()
                .filter(t -> finalPrefix == null || finalPrefix.isBlank()
                        || t.toUpperCase().startsWith(finalPrefix.toUpperCase()))
                .toList();

        return new ToolResult(String.join("\n", matching));
    }

    public int getCallCount()                  { return receivedArguments.size(); }
    public List<String> getReceivedArguments()  { return receivedArguments; }
    public String getLastArguments() {
        return receivedArguments.isEmpty() ? null : receivedArguments.get(receivedArguments.size() - 1);
    }
}

