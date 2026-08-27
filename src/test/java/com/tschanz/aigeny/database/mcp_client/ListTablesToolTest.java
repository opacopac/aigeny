package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListTablesTool")
class ListTablesToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private ListTablesTool tool;

    @BeforeEach
    void setUp() {
        tool = new ListTablesTool(connection, objectMapper);
    }

    @Test
    @DisplayName("getName() returns 'list_tables'")
    void getName() {
        assertThat(tool.getName()).isEqualTo("list_tables");
    }

    @Test
    @DisplayName("getCallDescription() mentions the prefix when present")
    void mentionsPrefix() {
        assertThat(tool.getCallDescription("{\"prefix\":\"A_\"}")).contains("A_");
    }

    @Test
    @DisplayName("getCallDescription() has a plain fallback without a prefix")
    void plainWithoutPrefix() {
        assertThat(tool.getCallDescription("{}")).isEqualTo("Tabellen auflisten");
    }
}

