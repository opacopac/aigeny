package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for the Oracle DB MCP client-side tools ({@link ListTablesTool}, {@link DescribeTableTool},
 * {@link SearchSchemaTool}, {@link SampleTableTool}, {@link RunQueryTool}).
 *
 * <p>Each concrete subclass owns exactly one tool name and its {@code getCallDescription()}
 * logic (how to summarize its own specific arguments for the UI typing indicator). Everything
 * else that is common to all five - resolving description/JSON-schema from the MCP server's
 * {@code listTools()} response, invoking the tool via {@link OracleMcpConnection}, and turning
 * the {@link McpSchema.CallToolResult} into a {@link ToolResult} (with optional {@link QueryResult}
 * for CSV export) - lives here.
 *
 * <p>The server ({@link OracleMcpServerLauncher} and its {@code OracleMcpToolHandler}
 * implementations, in the sibling {@code mcp_server} package) remains the single source
 * of truth for description and JSON schema; this class never hardcodes them.
 */
public abstract class AbstractOracleMcpTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(AbstractOracleMcpTool.class);
    private static final String MSG_NOT_CONFIGURED = "db.error.not_configured";

    protected final OracleMcpConnection connection;

    protected AbstractOracleMcpTool(OracleMcpConnection connection, ObjectMapper objectMapper) {
        super(objectMapper);
        this.connection = connection;
    }

    @Override
    public String getDescription() {
        return connection.getToolInfo(getName())
                .map(McpSchema.Tool::description)
                .orElse("Oracle DB tool '" + getName() + "' (not available - database not configured " +
                        "or the MCP server is not reachable)");
    }

    @Override
    public ToolDefinition getDefinition() {
        Optional<McpSchema.Tool> info = connection.getToolInfo(getName());
        if (info.isEmpty()) {
            // MCP server not reachable (yet) - minimal fallback schema so the app still starts up cleanly.
            return new ToolDefinition(getName(), getDescription(),
                    Map.of("type", "object", "properties", Map.of()));
        }
        McpSchema.JsonSchema schema = info.get().inputSchema();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", schema.type() != null ? schema.type() : "object");
        parameters.put("properties", schema.properties() != null ? schema.properties() : Map.of());
        if (schema.required() != null && !schema.required().isEmpty()) {
            parameters.put("required", schema.required());
        }
        return new ToolDefinition(getName(), getDescription(), parameters);
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!connection.isAvailable()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        Map<String, Object> arguments = objectMapper.readValue(argumentsJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        log.info("  DB TOOL REQUEST name={} args={}", getName(), arguments);
        if (arguments.get("sql") != null) {
            // Dedicated debug line with the raw SQL string (run_query), independent of the
            // generic args map above - easiest to grep for when debugging a specific query.
            log.debug("  SQL: {}", arguments.get("sql"));
        }

        long t0 = System.currentTimeMillis();
        McpSchema.CallToolResult result = connection.callTool(getName(), arguments);
        long elapsed = System.currentTimeMillis() - t0;

        List<McpSchema.Content> content = result.content();
        String text = content.isEmpty() ? "" : asText(content.get(0));

        if (Boolean.TRUE.equals(result.isError())) {
            log.error("  DB TOOL {} FAILED elapsed={}ms error=\"{}\"", getName(), elapsed, text);
            return new ToolResult(text);
        }

        QueryResult qr = tryParseStructuredResult(content);
        log.info("  DB TOOL {} OK elapsed={}ms rows={}", getName(), elapsed, qr == null ? 0 : qr.getRows().size());
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
            log.warn("Could not parse structured MCP result for tool {}: {}", getName(), e.getMessage());
            return null;
        }
    }

    private static String asText(McpSchema.Content c) {
        return (c instanceof McpSchema.TextContent tc) ? tc.text() : "";
    }
}

