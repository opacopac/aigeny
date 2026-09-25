package com.tschanz.aigeny.integration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Mock of a database MCP tool (e.g. the Oracle {@code list_tables} tool normally provided at
 * runtime by {@code OracleMcpToolProvider}/{@code GenericOracleMcpTool}). Lets tests simulate the
 * database MCP server's responses without a real Oracle connection or MCP transport.
 *
 * <p>Extends {@link GenericMockTool} for the shared name/description/call-recording boilerplate,
 * but overrides {@link #execute(String)} since the response depends on the arguments (the
 * {@code prefix} filter) rather than being a fixed canned response.
 *
 * <p>Currently understands the {@code list_tables} shape (optional {@code prefix} argument,
 * case-insensitive filter over a canned table list) since that's what the initial "main loop"
 * integration test exercises; extend as more DB tool scenarios are needed.
 */
public class MockDatabaseTool extends GenericMockTool {

    public static final String LIST_TABLES = "list_tables";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> tableNames;

    public MockDatabaseTool(String name, List<String> tableNames) {
        super(name, "Mock database MCP tool: " + name);
        this.tableNames = tableNames;
    }

    /** Convenience factory: a {@code list_tables} mock backed by the given canned table names. */
    public static MockDatabaseTool listTables(String... tableNames) {
        return new MockDatabaseTool(LIST_TABLES, List.of(tableNames));
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(getName(), getDescription(), Map.of(
                "type", "object",
                "properties", Map.of("prefix", Map.of("type", "string",
                        "description", "Optional case-insensitive prefix filter for table names."))));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        recordCall(argumentsJson);

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
}

