package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Client-side {@code describe_table} tool - see {@link DescribeTableHandler} for the server-side logic. */
@Service
public class DescribeTableTool extends AbstractOracleMcpTool {

    public static final String NAME = "describe_table";

    public DescribeTableTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(connection, objectMapper);
    }

    @Override public String getName() { return NAME; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            return "Tabelle beschreiben: " + args.path("table").asText("");
        } catch (Exception e) {
            return NAME;
        }
    }
}
