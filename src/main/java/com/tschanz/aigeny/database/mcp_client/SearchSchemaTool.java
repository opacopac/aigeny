package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Client-side {@code search_schema} tool - see {@link SearchSchemaHandler} for the server-side logic. */
@Service
public class SearchSchemaTool extends AbstractOracleMcpTool {

    public static final String NAME = "search_schema";

    public SearchSchemaTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(connection, objectMapper);
    }

    @Override public String getName() { return NAME; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            return "Schema durchsuchen: " + args.path("term").asText("");
        } catch (Exception e) {
            return NAME;
        }
    }
}

