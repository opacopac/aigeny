package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A single generic client-side tool that proxies exactly one tool call to the embedded
 * Oracle DB MCP server ({@link OracleMcpConnection}).
 *
 * <p>This class deliberately does not hardcode a tool name, description, JSON schema or any
 * argument names anywhere. One instance is created per tool name that the server actually
 * reports via {@code listTools()} - see {@link OracleMcpToolProvider}, which is the only place
 * that turns the server's response into a set of {@code Tool} instances. Adding, removing or
 * changing a tool on the server side (a new/changed {@code OracleMcpToolHandler} - see the
 * sibling {@code mcp_server} package) is therefore automatically reflected here without any
 * client-side code change.
 */
public class GenericOracleMcpTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(GenericOracleMcpTool.class);
    private static final String MSG_NOT_CONFIGURED = "db.error.not_configured";

    private final String name;
    private final OracleMcpConnection connection;

    public GenericOracleMcpTool(String name, OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(objectMapper);
        this.name = name;
        this.connection = connection;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return connection.getToolInfo(name)
                .map(McpSchema.Tool::description)
                .orElse("Oracle DB tool '" + name + "' (not available - database not configured " +
                        "or the MCP server is not reachable)");
    }

    @Override
    public ToolDefinition getDefinition() {
        Optional<McpSchema.Tool> info = connection.getToolInfo(name);
        if (info.isEmpty()) {
            // MCP server not reachable (yet) - minimal fallback schema so the app still starts up cleanly.
            return new ToolDefinition(name, getDescription(),
                    Map.of("type", "object", "properties", Map.of()));
        }
        McpSchema.JsonSchema schema = info.get().inputSchema();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", schema.type() != null ? schema.type() : "object");
        parameters.put("properties", schema.properties() != null ? schema.properties() : Map.of());
        if (schema.required() != null && !schema.required().isEmpty()) {
            parameters.put("required", schema.required());
        }
        return new ToolDefinition(name, getDescription(), parameters);
    }

    /**
     * Generic call-description for the UI typing indicator: prefers an explicit
     * {@code description} argument (used e.g. by {@code run_query}); otherwise summarizes the
     * call by joining the values of all scalar top-level arguments after the humanized tool
     * name, so this stays useful without knowing any tool-specific argument names.
     */
    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            JsonNode desc = args.get("description");
            if (desc != null && !desc.isNull() && !desc.asText().isBlank()) {
                return desc.asText();
            }

            StringBuilder sb = new StringBuilder(humanize(name));
            boolean any = false;
            Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
                    sb.append(any ? ", " : ": ").append(value.asText());
                    any = true;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return name;
        }
    }

    private static String humanize(String toolName) {
        return toolName.replace('_', ' ');
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!connection.isAvailable()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        Map<String, Object> arguments = objectMapper.readValue(argumentsJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        log.info("  DB TOOL REQUEST name={} args={}", name, arguments);
        if (arguments.get("sql") != null) {
            // Dedicated debug line with the raw SQL string (run_query), independent of the
            // generic args map above - easiest to grep for when debugging a specific query.
            log.debug("  SQL: {}", arguments.get("sql"));
        }

        long t0 = System.currentTimeMillis();
        McpSchema.CallToolResult result = connection.callTool(name, arguments);
        long elapsed = System.currentTimeMillis() - t0;

        List<McpSchema.Content> content = result.content();
        String text = content.isEmpty() ? "" : asText(content.get(0));

        if (Boolean.TRUE.equals(result.isError())) {
            log.error("  DB TOOL {} FAILED elapsed={}ms error=\"{}\"", name, elapsed, text);
            return new ToolResult(text);
        }

        QueryResult qr = tryParseStructuredResult(content);
        log.info("  DB TOOL {} OK elapsed={}ms rows={}", name, elapsed, qr == null ? 0 : qr.getRows().size());
        return qr != null ? new ToolResult(text, qr) : new ToolResult(text);
    }

    /** Parses the second content block (JSON columns/rows), added by the server for CSV export. */
    @SuppressWarnings("unchecked")
    private QueryResult tryParseStructuredResult(List<McpSchema.Content> content) {
        if (content.size() < 2) return null;
        try {
            JsonNode structured = objectMapper.readTree(asText(content.get(1)));
            List<String> columns = objectMapper.convertValue(structured.get("columns"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<Map<String, Object>> rows = objectMapper.convertValue(structured.get("rows"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return new QueryResult("Oracle DB", columns, rows);
        } catch (Exception e) {
            log.warn("Could not parse structured MCP result for tool {}: {}", name, e.getMessage());
            return null;
        }
    }

    private static String asText(McpSchema.Content c) {
        return (c instanceof McpSchema.TextContent tc) ? tc.text() : "";
    }
}

