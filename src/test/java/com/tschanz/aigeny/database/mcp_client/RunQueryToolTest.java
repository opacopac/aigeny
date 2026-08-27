package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RunQueryTool}. Also exercises the shared execute()/getDescription()/
 * getDefinition() logic in {@link AbstractOracleMcpTool}, which all five client-side Oracle
 * DB tools inherit - the other tool classes only add lighter metadata/call-description tests
 * (see {@link ListTablesToolTest}, {@link DescribeTableToolTest}, {@link SearchSchemaToolTest},
 * {@link SampleTableToolTest}).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RunQueryTool")
class RunQueryToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private RunQueryTool tool;

    @BeforeEach
    void setUp() {
        tool = new RunQueryTool(connection, objectMapper);
    }

    @Nested
    @DisplayName("Tool metadata")
    class Metadata {

        @Test
        @DisplayName("getName() returns 'run_query'")
        void getName() {
            assertThat(tool.getName()).isEqualTo("run_query");
        }

        @Test
        @DisplayName("getDescription() falls back to a generic message when server info is unavailable")
        void getDescriptionFallback() {
            when(connection.getToolInfo("run_query")).thenReturn(Optional.empty());
            assertThat(tool.getDescription()).contains("run_query").contains("not available");
        }

        @Test
        @DisplayName("getDescription() returns the server-declared description when available")
        void getDescriptionFromServer() {
            McpSchema.Tool serverTool = new McpSchema.Tool("run_query", "Run a SELECT query",
                    new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null));
            when(connection.getToolInfo("run_query")).thenReturn(Optional.of(serverTool));

            assertThat(tool.getDescription()).isEqualTo("Run a SELECT query");
        }

        @Test
        @DisplayName("getDefinition() builds parameters from the server's JSON schema")
        void getDefinitionFromServerSchema() {
            McpSchema.Tool serverTool = new McpSchema.Tool("run_query", "Run a SELECT query",
                    new McpSchema.JsonSchema("object",
                            Map.of("sql", Map.of("type", "string")),
                            List.of("sql"), null, null, null));
            when(connection.getToolInfo("run_query")).thenReturn(Optional.of(serverTool));

            var definition = tool.getDefinition();

            assertThat(definition.getFunction().getName()).isEqualTo("run_query");
            assertThat(definition.getFunction().getDescription()).isEqualTo("Run a SELECT query");
            assertThat(definition.getFunction().getParameters()).containsEntry("type", "object");
            assertThat(definition.getFunction().getParameters()).containsKey("properties");
            assertThat(definition.getFunction().getParameters()).containsEntry("required", List.of("sql"));
        }

        @Test
        @DisplayName("getDefinition() falls back to an empty schema when server info is unavailable")
        void getDefinitionFallback() {
            when(connection.getToolInfo("run_query")).thenReturn(Optional.empty());

            var definition = tool.getDefinition();

            assertThat(definition.getFunction().getName()).isEqualTo("run_query");
            assertThat(definition.getFunction().getParameters()).containsEntry("type", "object");
        }
    }

    @Nested
    @DisplayName("getCallDescription()")
    class CallDescription {

        @Test
        @DisplayName("uses the 'description' field")
        void usesDescriptionField() {
            assertThat(tool.getCallDescription("{\"sql\":\"SELECT 1\",\"description\":\"Count rows\"}"))
                    .isEqualTo("Count rows");
        }

        @Test
        @DisplayName("falls back to the tool name when no description is given")
        void fallsBackToName() {
            assertThat(tool.getCallDescription("{\"sql\":\"SELECT 1\"}")).isEqualTo("run_query");
        }
    }

    @Nested
    @DisplayName("execute() when MCP server not connected")
    class NotConnected {

        @Test
        @DisplayName("returns 'not configured' message without calling the connection")
        void returnsNotConfiguredMessage() throws Exception {
            when(connection.isAvailable()).thenReturn(false);

            ToolResult result = tool.execute("{\"sql\":\"SELECT 1\",\"description\":\"test\"}");

            assertThat(result.getText()).contains("not configured");
            verify(connection, never()).callTool(any(), any());
        }
    }

    @Nested
    @DisplayName("execute() with successful MCP call")
    class SuccessfulExecution {

        @BeforeEach
        void arrange() {
            when(connection.isAvailable()).thenReturn(true);
        }

        @Test
        @DisplayName("passes parsed JSON arguments through to connection.callTool()")
        void sendsCorrectRequest() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("(no rows returned)", false));

            tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"ping\"}");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(connection).callTool(eq("run_query"), captor.capture());

            assertThat(captor.getValue()).containsEntry("sql", "SELECT 1 FROM DUAL");
            assertThat(captor.getValue()).containsEntry("description", "ping");
        }

        @Test
        @DisplayName("returns text from the first content block")
        void returnsTextFromFirstBlock() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("ID | NAME\nAlice", false));

            ToolResult result = tool.execute("{\"sql\":\"SELECT * FROM USERS\",\"description\":\"x\"}");

            assertThat(result.getText()).isEqualTo("ID | NAME\nAlice");
        }

        @Test
        @DisplayName("parses the second content block into a QueryResult for CSV export")
        void parsesStructuredSecondBlock() throws Exception {
            String structuredJson = objectMapper.writeValueAsString(Map.of(
                    "columns", List.of("ID", "NAME"),
                    "rows", List.of(Map.of("ID", 1, "NAME", "Alice"))
            ));
            when(connection.callTool(eq("run_query"), any())).thenReturn(new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("ID | NAME\nAlice"), new McpSchema.TextContent(structuredJson)),
                    false));

            ToolResult result = tool.execute("{\"sql\":\"SELECT * FROM USERS\",\"description\":\"x\"}");

            assertThat(result.hasQueryResult()).isTrue();
            assertThat(result.getQueryResult().getColumns()).containsExactly("ID", "NAME");
            assertThat(result.getQueryResult().getRows()).hasSize(1);
        }

        @Test
        @DisplayName("has no QueryResult when only a single content block is returned")
        void noQueryResultWithSingleBlock() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("(no rows returned)", false));

            ToolResult result = tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"x\"}");

            assertThat(result.hasQueryResult()).isFalse();
        }

        @Test
        @DisplayName("gracefully ignores an unparsable second content block")
        void gracefullyHandlesMalformedStructuredBlock() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("some text"), new McpSchema.TextContent("not-json")),
                    false));

            ToolResult result = tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"x\"}");

            assertThat(result.getText()).isEqualTo("some text");
            assertThat(result.hasQueryResult()).isFalse();
        }
    }

    @Nested
    @DisplayName("execute() with error result from the MCP server")
    class ErrorExecution {

        @BeforeEach
        void arrange() {
            when(connection.isAvailable()).thenReturn(true);
        }

        @Test
        @DisplayName("returns the error text without a QueryResult")
        void returnsErrorText() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("ERROR: Only SELECT queries are allowed.", true));

            ToolResult result = tool.execute("{\"sql\":\"DELETE FROM USERS\",\"description\":\"evil\"}");

            assertThat(result.getText()).contains("Only SELECT");
            assertThat(result.hasQueryResult()).isFalse();
        }
    }
}

