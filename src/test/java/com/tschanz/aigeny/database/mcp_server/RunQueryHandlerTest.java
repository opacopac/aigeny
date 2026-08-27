package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RunQueryHandler")
class RunQueryHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RunQueryHandler handler = new RunQueryHandler();

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData resultSetMetaData;

    @Test
    @DisplayName("exposes name/description/schema for MCP registration")
    void exposesMetadata() {
        assertThat(handler.name()).isEqualTo("run_query");
        assertThat(handler.description()).isNotBlank();
        assertThat(handler.schemaJson()).contains("sql");
    }

    @Nested
    @DisplayName("SQL validation")
    class Validation {

        @Test
        @DisplayName("rejects non-SELECT statement")
        void rejectsNonSelect() {
            McpSchema.CallToolResult result = handler.handle(
                    null, objectMapper, Map.of("sql", "UPDATE foo SET x=1", "description", "hack"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("SELECT");
        }

        @Test
        @DisplayName("rejects statement containing INSERT keyword")
        void rejectsInsert() {
            McpSchema.CallToolResult result = handler.handle(
                    null, objectMapper, Map.of("sql", "SELECT * FROM t WHERE x = 'INSERT INTO'", "description", "x"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("dangerous");
        }

        @Test
        @DisplayName("rejects DELETE statement")
        void rejectsDelete() {
            McpSchema.CallToolResult result = handler.handle(
                    null, objectMapper, Map.of("sql", "DELETE FROM users", "description", "evil"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("SELECT");
        }

        @Test
        @DisplayName("returns 'connect' error when DataSource is null but SQL is valid")
        void returnsNoConnectionMessage() {
            McpSchema.CallToolResult result = handler.handle(
                    null, objectMapper, Map.of("sql", "SELECT 1 FROM DUAL", "description", "test"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("connect");
        }
    }

    @Nested
    @DisplayName("successful execution")
    class Success {

        @BeforeEach
        void arrange() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(2);
            when(resultSetMetaData.getColumnLabel(1)).thenReturn("ID");
            when(resultSetMetaData.getColumnLabel(2)).thenReturn("NAME");
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getObject(1)).thenReturn(1, 2);
            when(resultSet.getObject(2)).thenReturn("Alice", "Bob");
        }

        @Test
        @DisplayName("returns non-error CallToolResult")
        void returnsNonErrorResult() {
            assertThat(execute().isError()).isFalse();
        }

        @Test
        @DisplayName("first content block contains human-readable text with column names and row data")
        void firstBlockIsHumanReadableText() {
            String text = textOf(execute());
            assertThat(text).contains("ID").contains("NAME");
            assertThat(text).contains("Alice").contains("Bob");
        }

        @Test
        @DisplayName("second content block is structured JSON with columns and rows")
        void secondBlockIsStructuredJson() throws Exception {
            McpSchema.CallToolResult result = execute();
            assertThat(result.content()).hasSize(2);

            String json = ((McpSchema.TextContent) result.content().get(1)).text();
            var node = objectMapper.readTree(json);

            assertThat(node.get("columns").get(0).asText()).isEqualTo("ID");
            assertThat(node.get("columns").get(1).asText()).isEqualTo("NAME");
            assertThat(node.get("rows")).hasSize(2);
        }

        @Test
        @DisplayName("sets fetch size and applies the default row limit as max rows")
        void setsMaxRowsAndFetchSize() throws Exception {
            execute();
            verify(preparedStatement).setMaxRows(200);
            verify(preparedStatement).setFetchSize(200);
        }

        @Test
        @DisplayName("honors an explicit 'limit' argument, clamped to MAX_ROWS")
        void honorsExplicitLimit() throws Exception {
            handler.handle(dataSource, objectMapper,
                    Map.of("sql", "SELECT ID, NAME FROM USERS", "description", "x", "limit", 10000));
            verify(preparedStatement).setMaxRows(5000);
        }

        private McpSchema.CallToolResult execute() {
            return handler.handle(dataSource, objectMapper,
                    Map.of("sql", "SELECT ID, NAME FROM USERS", "description", "get users"));
        }
    }

    @Nested
    @DisplayName("SQL execution error handling")
    class ErrorHandling {

        @BeforeEach
        void arrange() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenThrow(new SQLException("ORA-00942: table not found"));
        }

        @Test
        @DisplayName("returns error CallToolResult containing the exception message")
        void returnsSqlErrorMessage() {
            McpSchema.CallToolResult result = handler.handle(
                    dataSource, objectMapper, Map.of("sql", "SELECT * FROM NONEXISTENT_TABLE", "description", "test"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("ORA-00942");
        }

        @Test
        @DisplayName("does not throw - exceptions are converted to an error result")
        void doesNotThrow() {
            org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                    handler.handle(dataSource, objectMapper,
                            Map.of("sql", "SELECT * FROM BAD_TABLE", "description", "test")));
        }
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }
}


