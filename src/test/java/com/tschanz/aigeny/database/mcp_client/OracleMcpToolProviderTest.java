package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OracleMcpToolProvider} - verifies that the set of exposed tools is
 * derived purely from {@link OracleMcpConnection#getDiscoveredToolNames()}, with nothing
 * hardcoded on the client side.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OracleMcpToolProvider")
class OracleMcpToolProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private OracleMcpToolProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OracleMcpToolProvider(connection, objectMapper);
    }

    @Test
    @DisplayName("returns no tools when the server hasn't reported any (e.g. DB not configured)")
    void noToolsWhenNoneDiscovered() {
        when(connection.getDiscoveredToolNames()).thenReturn(List.of());

        assertThat(provider.getTools()).isEmpty();
    }

    @Test
    @DisplayName("returns one GenericOracleMcpTool per discovered tool name, preserving order")
    void oneToolPerDiscoveredName() {
        when(connection.getDiscoveredToolNames()).thenReturn(
                List.of("list_tables", "describe_table", "search_schema", "sample_table", "run_query"));

        List<Tool> tools = provider.getTools();

        assertThat(tools).hasSize(5);
        assertThat(tools).allMatch(t -> t instanceof GenericOracleMcpTool);
        assertThat(tools.stream().map(Tool::getName)).containsExactly(
                "list_tables", "describe_table", "search_schema", "sample_table", "run_query");
    }

    @Test
    @DisplayName("reflects a newly discovered/renamed server tool without any client-side code change")
    void reflectsArbitraryNewToolName() {
        when(connection.getDiscoveredToolNames()).thenReturn(List.of("some_future_tool"));

        List<Tool> tools = provider.getTools();

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("some_future_tool");
    }
}
