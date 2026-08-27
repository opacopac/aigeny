package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Client-side {@code list_tables} tool - see {@link ListTablesHandler} for the server-side logic. */
@Service
public class ListTablesTool extends AbstractOracleMcpTool {

    public static final String NAME = "list_tables";

    public ListTablesTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(connection, objectMapper);
    }

    @Override public String getName() { return NAME; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String prefix = args.path("prefix").asText("");
            return prefix.isBlank() ? "Tabellen auflisten" : "Tabellen auflisten (Prefix: " + prefix + ")";
        } catch (Exception e) {
            return NAME;
        }
    }
}

