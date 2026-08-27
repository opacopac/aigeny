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
@DisplayName("DescribeTableTool")
class DescribeTableToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private DescribeTableTool tool;

    @BeforeEach
    void setUp() {
        tool = new DescribeTableTool(connection, objectMapper);
    }

    @Test
    @DisplayName("getName() returns 'describe_table'")
    void getName() {
        assertThat(tool.getName()).isEqualTo("describe_table");
    }

    @Test
    @DisplayName("getCallDescription() mentions the table name")
    void mentionsTable() {
        assertThat(tool.getCallDescription("{\"table\":\"A_SORTIMENT_E\"}")).contains("A_SORTIMENT_E");
    }
}

