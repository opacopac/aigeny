package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Client-side {@code run_query} tool - see {@link RunQueryHandler} for the server-side logic. */
@Service
public class RunQueryTool extends AbstractOracleMcpTool {

    public static final String NAME = "run_query";

    public RunQueryTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(connection, objectMapper);
    }

    @Override public String getName() { return NAME; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String desc = args.path("description").asText("");
            return desc.isBlank() ? NAME : desc;
        } catch (Exception e) {
            return NAME;
        }
    }
}

