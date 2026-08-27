package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Client-side {@code sample_table} tool - see {@link SampleTableHandler} for the server-side logic. */
@Service
public class SampleTableTool extends AbstractOracleMcpTool {

    public static final String NAME = "sample_table";

    public SampleTableTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(connection, objectMapper);
    }

    @Override public String getName() { return NAME; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            return "Beispieldaten laden: " + args.path("table").asText("");
        } catch (Exception e) {
            return NAME;
        }
    }
}

