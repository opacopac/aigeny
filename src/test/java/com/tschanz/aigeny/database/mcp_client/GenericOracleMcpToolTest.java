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
 * Unit tests for {@link GenericOracleMcpTool}, the single generic client-side tool that
 * replaced the five hand-written {@code *Tool} classes (one per Oracle DB MCP server tool).
 * A separate instance is created per discovered tool name - here exercised with
 * {@code "run_query"} (needs an argument set to verify execute()/getDescription()/
 * getDefinition()) and a couple of other names to verify the generic call-description logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GenericOracleMcpTool")
class GenericOracleMcpToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private GenericOracleMcpTool tool(String name) {
        return new GenericOracleMcpTool(name, connection, objectMapper);
    }

    @Nested
    @DisplayName("Tool metadata")
    class Metadata {

        @Test
        @DisplayName("getName() returns the name it was constructed with")
        void getName() {
            assertThat(tool("run_query").getName()).isEqualTo("run_query");
            assertThat(tool("list_tables").getName()).isEqualTo("list_tables");
        }

        @Test
        @DisplayName("getDescription() falls back to a generic message when server info is unavailable")
        void getDescriptionFallback() {
            when(connection.getToolInfo("run_query")).thenReturn(Optional.empty());
            assertThat(tool("run_query").getDescription()).contains("run_query").contains("not available");
        }

        @Test
        @DisplayName("getDescription() returns the server-declared description when available")
        void getDescriptionFromServer() {
            McpSchema.Tool serverTool = McpSchema.Tool.builder()
                    .name("run_query")
                    .description("Run a SELECT query")
                    .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null))
                    .build();
            when(connection.getToolInfo("run_query")).thenReturn(Optional.of(serverTool));

            assertThat(tool("run_query").getDescription()).isEqualTo("Run a SELECT query");
        }

        @Test
        @DisplayName("getDefinition() builds parameters from the server's JSON schema")
        void getDefinitionFromServerSchema() {
            McpSchema.Tool serverTool = McpSchema.Tool.builder()
                    .name("run_query")
                    .description("Run a SELECT query")
                    .inputSchema(new McpSchema.JsonSchema("object",
                            Map.of("sql", Map.of("type", "string")),
                            List.of("sql"), null, null, null))
                    .build();
            when(connection.getToolInfo("run_query")).thenReturn(Optional.of(serverTool));

            var definition = tool("run_query").getDefinition();

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

            var definition = tool("run_query").getDefinition();

            assertThat(definition.getFunction().getName()).isEqualTo("run_query");
            assertThat(definition.getFunction().getParameters()).containsEntry("type", "object");
        }
    }

    @Nested
    @DisplayName("getCallDescription()")
    class CallDescription {

        @Test
        @DisplayName("uses the 'description' field when present")
        void usesDescriptionField() {
            assertThat(tool("run_query").getCallDescription("{\"sql\":\"SELECT 1\",\"description\":\"Count rows\"}"))
                    .isEqualTo("Count rows");
        }

        @Test
        @DisplayName("falls back to a humanized name + argument values when no description is given")
        void fallsBackToNameAndArgs() {
            assertThat(tool("run_query").getCallDescription("{\"sql\":\"SELECT 1\"}"))
                    .isEqualTo("run query: SELECT 1");
        }

        @Test
        @DisplayName("mentions the table name for describe_table-like calls")
        void mentionsTable() {
            assertThat(tool("describe_table").getCallDescription("{\"table\":\"A_SORTIMENT_E\"}"))
                    .contains("A_SORTIMENT_E");
        }

        @Test
        @DisplayName("mentions the prefix for list_tables-like calls when present")
        void mentionsPrefix() {
            assertThat(tool("list_tables").getCallDescription("{\"prefix\":\"A_\"}")).contains("A_");
        }

        @Test
        @DisplayName("has a plain humanized fallback without any arguments")
        void plainWithoutArgs() {
            assertThat(tool("list_tables").getCallDescription("{}")).isEqualTo("list tables");
        }

        @Test
        @DisplayName("mentions the search term for search_schema-like calls")
        void mentionsTerm() {
            assertThat(tool("search_schema").getCallDescription("{\"term\":\"SORTIMENT\"}")).contains("SORTIMENT");
        }

        @Test
        @DisplayName("falls back to the raw name on unparsable JSON")
        void fallsBackOnUnparsableJson() {
            assertThat(tool("run_query").getCallDescription("not-json")).isEqualTo("run_query");
        }
    }

    @Nested
    @DisplayName("execute() when MCP server not connected")
    class NotConnected {

        @Test
        @DisplayName("returns 'not configured' message without calling the connection")
        void returnsNotConfiguredMessage() throws Exception {
            when(connection.isAvailable()).thenReturn(false);

            ToolResult result = tool("run_query").execute("{\"sql\":\"SELECT 1\",\"description\":\"test\"}");

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
        @DisplayName("passes parsed JSON arguments through to connection.callTool() under this tool's name")
        void sendsCorrectRequest() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("(no rows returned)", false));

            tool("run_query").execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"ping\"}");

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

            ToolResult result = tool("run_query").execute("{\"sql\":\"SELECT * FROM USERS\",\"description\":\"x\"}");

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

            ToolResult result = tool("run_query").execute("{\"sql\":\"SELECT * FROM USERS\",\"description\":\"x\"}");

            assertThat(result.hasQueryResult()).isTrue();
            assertThat(result.getQueryResult().getColumns()).containsExactly("ID", "NAME");
            assertThat(result.getQueryResult().getRows()).hasSize(1);
        }

        @Test
        @DisplayName("has no QueryResult when only a single content block is returned")
        void noQueryResultWithSingleBlock() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(
                    new McpSchema.CallToolResult("(no rows returned)", false));

            ToolResult result = tool("run_query").execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"x\"}");

            assertThat(result.hasQueryResult()).isFalse();
        }

        @Test
        @DisplayName("gracefully ignores an unparsable second content block")
        void gracefullyHandlesMalformedStructuredBlock() throws Exception {
            when(connection.callTool(eq("run_query"), any())).thenReturn(new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("some text"), new McpSchema.TextContent("not-json")),
                    false));

            ToolResult result = tool("run_query").execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"x\"}");

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

            ToolResult result = tool("run_query").execute("{\"sql\":\"DELETE FROM USERS\",\"description\":\"evil\"}");

            assertThat(result.getText()).contains("Only SELECT");
            assertThat(result.hasQueryResult()).isFalse();
        }
    }
}

