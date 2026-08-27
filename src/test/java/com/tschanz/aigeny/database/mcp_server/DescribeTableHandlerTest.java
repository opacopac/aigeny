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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DescribeTableHandler")
class DescribeTableHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DescribeTableHandler handler = new DescribeTableHandler();

    @Mock private DataSource dataSource;
    @Mock private Connection connection;

    @Test
    @DisplayName("exposes name/description/schema for MCP registration")
    void exposesMetadata() {
        assertThat(handler.name()).isEqualTo("describe_table");
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
        }

        @Test
        @DisplayName("rejects an invalid table identifier")
        void rejectsInvalidIdentifier() {
            McpSchema.CallToolResult result = handler.handle(dataSource, objectMapper, Map.of("table", "FOO;DROP"));
            assertThat(result.isError()).isTrue();
        }

        @Test
        @DisplayName("returns 'not found' error when no columns are visible")
        void returnsNotFoundWhenNoColumns() throws Exception {
            // Schema-qualified name avoids the extra currentSchema() lookup query, keeping the mock simple.
            when(dataSource.getConnection()).thenReturn(connection);
            PreparedStatement columnsStmt = org.mockito.Mockito.mock(PreparedStatement.class);
            ResultSet emptyRs = org.mockito.Mockito.mock(ResultSet.class);
            ResultSetMetaData emptyMeta = org.mockito.Mockito.mock(ResultSetMetaData.class);
            when(connection.prepareStatement(anyString())).thenReturn(columnsStmt);
            when(columnsStmt.executeQuery()).thenReturn(emptyRs);
            when(emptyRs.getMetaData()).thenReturn(emptyMeta);
            when(emptyMeta.getColumnCount()).thenReturn(1);
            when(emptyMeta.getColumnLabel(1)).thenReturn("COLUMN_NAME");
            when(emptyRs.next()).thenReturn(false);

            McpSchema.CallToolResult result = handler.handle(
                    dataSource, objectMapper, Map.of("table", "TESTSCHEMA.NOT_EXISTING"));

            assertThat(result.isError()).isTrue();
            assertThat(textOf(result)).contains("not found");
        }
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().isEmpty() ? "" : ((McpSchema.TextContent) result.content().get(0)).text();
    }
}

