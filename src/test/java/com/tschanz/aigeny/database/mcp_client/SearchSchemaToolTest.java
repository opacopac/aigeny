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
@DisplayName("SearchSchemaTool")
class SearchSchemaToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private OracleMcpConnection connection;

    private SearchSchemaTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchSchemaTool(connection, objectMapper);
    }

    @Test
    @DisplayName("getName() returns 'search_schema'")
    void getName() {
        assertThat(tool.getName()).isEqualTo("search_schema");
    }

    @Test
    @DisplayName("getCallDescription() mentions the search term")
    void mentionsTerm() {
        assertThat(tool.getCallDescription("{\"term\":\"SORTIMENT\"}")).contains("SORTIMENT");
    }
}

