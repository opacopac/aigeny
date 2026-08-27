package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("ListTablesHandler")
class ListTablesHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ListTablesHandler handler = new ListTablesHandler();

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData resultSetMetaData;

    @Test
    @DisplayName("exposes name/description/schema for MCP registration")
    void exposesMetadata() {
        assertThat(handler.name()).isEqualTo("list_tables");
        assertThat(handler.description()).isNotBlank();
        assertThat(handler.schemaJson()).contains("prefix");
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @BeforeEach
        void arrange() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(1);
            when(resultSetMetaData.getColumnLabel(1)).thenReturn("TABLE_NAME");
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getObject(1)).thenReturn("A_SORTIMENT_E");
        }

        @Test
        @DisplayName("adds a prefix filter to the SQL when 'prefix' is given")
        void addsPrefixFilter() throws Exception {
            handler.handle(dataSource, objectMapper, Map.of("prefix", "A_"));

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(connection).prepareStatement(sqlCaptor.capture(), anyInt(), anyInt());
            assertThat(sqlCaptor.getValue()).contains("LIKE UPPER(?)");
            verify(preparedStatement).setObject(1, "A_");
        }

        @Test
        @DisplayName("omits the filter when 'prefix' is absent")
        void omitsFilterWhenNoPrefix() throws Exception {
            handler.handle(dataSource, objectMapper, Map.of());

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
            verify(connection).prepareStatement(sqlCaptor.capture(), anyInt(), anyInt());
            assertThat(sqlCaptor.getValue()).doesNotContain("LIKE");
        }

        @Test
        @DisplayName("returns a non-error result with the table name")
        void returnsTableList() {
            McpSchema.CallToolResult result = handler.handle(dataSource, objectMapper, Map.of());
            assertThat(result.isError()).isFalse();
            assertThat(textOf(result)).contains("A_SORTIMENT_E");
        }
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }
}

