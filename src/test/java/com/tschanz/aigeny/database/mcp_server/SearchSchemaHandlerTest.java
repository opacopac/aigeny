package com.tschanz.aigeny.database.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("SearchSchemaHandler")
class SearchSchemaHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchSchemaHandler handler = new SearchSchemaHandler();

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData resultSetMetaData;

    @Test
    @DisplayName("exposes name/description/schema for MCP registration")
    void exposesMetadata() {
        assertThat(handler.name()).isEqualTo("search_schema");
        assertThat(handler.description()).isNotBlank();
        assertThat(handler.schemaJson()).contains("term");
    }

    @Nested
    @DisplayName("handle()")
    class Handle {

        @Test
        @DisplayName("requires the 'term' argument")
        void requiresTerm() {
            McpSchema.CallToolResult result = handler.handle(dataSource, objectMapper, Map.of());
            assertThat(result.isError()).isTrue();
        }

        @Test
        @DisplayName("wraps the term in wildcards and binds it twice (table + column search)")
        void bindsWildcardTermTwice() throws Exception {
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(3);
            when(resultSetMetaData.getColumnLabel(1)).thenReturn("TABLE_NAME");
            when(resultSetMetaData.getColumnLabel(2)).thenReturn("COLUMN_NAME");
            when(resultSetMetaData.getColumnLabel(3)).thenReturn("MATCH_TYPE");
            when(resultSet.next()).thenReturn(false);

            handler.handle(dataSource, objectMapper, Map.of("term", "SORTIMENT"));

            verify(preparedStatement).setObject(1, "%SORTIMENT%");
            verify(preparedStatement).setObject(2, "%SORTIMENT%");
        }
    }
}

