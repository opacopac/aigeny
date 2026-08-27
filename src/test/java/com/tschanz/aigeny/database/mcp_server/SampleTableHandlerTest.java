package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SampleTableHandler")
class SampleTableHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SampleTableHandler handler = new SampleTableHandler();

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData resultSetMetaData;

    @Test
    @DisplayName("exposes name/description/schema for MCP registration")
    void exposesMetadata() {
        assertThat(handler.name()).isEqualTo("sample_table");
        assertThat(handler.description()).isNotBlank();
        assertThat(handler.schemaJson()).contains("table");
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @Test
        @DisplayName("requires the 'table' argument")
        void requiresTable() {
            McpSchema.CallToolResult result = handler.handle(dataSource, objectMapper, Map.of());
            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("required");
        }

        @Test
        @DisplayName("rejects an invalid table identifier")
        void rejectsInvalidIdentifier() {
            McpSchema.CallToolResult result = handler.handle(
                    dataSource, objectMapper, Map.of("table", "FOO; DROP TABLE BAR"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("invalid table name");
        }

        @Test
        @DisplayName("builds a FETCH FIRST ? ROWS ONLY query with the given/default limit")
        void buildsFetchFirstQuery() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(1);
            when(resultSetMetaData.getColumnLabel(1)).thenReturn("ID");
            when(resultSet.next()).thenReturn(false);

            handler.handle(dataSource, objectMapper, Map.of("table", "A_SORTIMENT_E"));

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(connection).prepareStatement(sqlCaptor.capture(), anyInt(), anyInt());
            assertThat(sqlCaptor.getValue()).contains("FETCH FIRST ? ROWS ONLY").contains("A_SORTIMENT_E");
            verify(preparedStatement).setMaxRows(20);
        }
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }
}

