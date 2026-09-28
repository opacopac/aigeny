package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.database.DataContextSelectionService;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    private static final String MSG_CONTEXT_MISMATCH = "db.error.context_mismatch";
    private static final String MSG_STAGE_MISMATCH = "db.error.stage_mismatch";

    private static final String ARG_DESCRIPTION = "description";
    private static final String ARG_CONTEXT = "context";
    private static final String ARG_STAGE = "stage";
    private static final String ARG_SQL = "sql";

    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_TYPE_OBJECT = "object";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";

    private static final String RESULT_COLUMNS = "columns";
    private static final String RESULT_ROWS = "rows";
    private static final String RESULT_SOURCE_NAME = "Oracle DB";

    private final String name;
    private final OracleMcpConnection connection;
    private final DataContextSelectionService dataContextSelectionService;

    public GenericOracleMcpTool(String name, OracleMcpConnection connection, ObjectMapper objectMapper,
                                 DataContextSelectionService dataContextSelectionService) {
        super(objectMapper);
        this.name = name;
        this.connection = connection;
        this.dataContextSelectionService = dataContextSelectionService;
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
                    Map.of(SCHEMA_TYPE, SCHEMA_TYPE_OBJECT, SCHEMA_PROPERTIES, Map.of()));
        }
        McpSchema.JsonSchema schema = info.get().inputSchema();
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(SCHEMA_TYPE, schema.type() != null ? schema.type() : SCHEMA_TYPE_OBJECT);
        parameters.put(SCHEMA_PROPERTIES, schema.properties() != null ? schema.properties() : Map.of());
        if (schema.required() != null && !schema.required().isEmpty()) {
            parameters.put(SCHEMA_REQUIRED, schema.required());
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
            JsonNode desc = args.get(ARG_DESCRIPTION);
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

    /**
     * Verifies that {@code arguments.get(key)} (if supplied by the LLM) matches the currently
     * configured/selected {@code expectedValue} for {@code context}/{@code stage}. Returns a
     * {@link ToolResult} carrying an error message when they differ, or {@code null} when the
     * argument is missing/blank or matches - i.e. the call may proceed.
     */
    private static ToolResult checkMatchesConfigured(Map<String, Object> arguments, String key,
                                                       String expectedValue, String messageKey) {
        Object rawValue = arguments.get(key);
        String value = rawValue == null ? null : String.valueOf(rawValue);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (expectedValue != null && !expectedValue.isBlank() && !expectedValue.equals(value)) {
            return new ToolResult(Messages.get(messageKey, value, expectedValue));
        }
        return null;
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!connection.isAvailable()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        Map<String, Object> arguments = objectMapper.readValue(argumentsJson,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));

        // "context"/"stage" are resolved per chat-request via DataContextSelectionService
        // (today effectively fixed from aigeny.db.default-context/default-stage, but designed
        // to support a future per-session UI selection - see DataContextSelectionService) -
        // reject the call if the LLM supplied a value for either argument that does not match
        // the currently selected context/stage, instead of silently overriding it.
        ToolResult contextMismatch = checkMatchesConfigured(arguments, ARG_CONTEXT,
                dataContextSelectionService.getContext(), MSG_CONTEXT_MISMATCH);
        if (contextMismatch != null) {
            return contextMismatch;
        }
        ToolResult stageMismatch = checkMatchesConfigured(arguments, ARG_STAGE,
                dataContextSelectionService.getStage(), MSG_STAGE_MISMATCH);
        if (stageMismatch != null) {
            return stageMismatch;
        }

        log.info("  DB TOOL REQUEST name={} args={}", name, arguments);
        if (arguments.get(ARG_SQL) != null) {
            // Dedicated debug line with the raw SQL string (run_query), independent of the
            // generic args map above - easiest to grep for when debugging a specific query.
            log.debug("  SQL: {}", arguments.get(ARG_SQL));
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

    /**
     * Extracts tabular data (columns + rows) for CSV export, trying two conventions in order:
     * <ol>
     *   <li>this app's own embedded server: a second content block whose text is
     *       {@code {"columns":[...string...], "rows":[...map-per-row...]}} (see
     *       {@code OracleSqlSupport#runSelect});</li>
     *   <li>a fallback for third-party/external MCP servers (e.g. {@code nova-mcp-db-exposer}),
     *       which generally return only a single content block and don't follow the two-block
     *       convention at all - see {@link #tryParseSingleBlockStructuredJson(List)}.</li>
     * </ol>
     */
    private QueryResult tryParseStructuredResult(List<McpSchema.Content> content) {
        QueryResult twoBlock = tryParseTwoBlockConvention(content);
        if (twoBlock != null) return twoBlock;
        return tryParseSingleBlockStructuredJson(content);
    }

    /** Parses the second content block (JSON columns/rows), added by our own embedded server for CSV export. */
    @SuppressWarnings("unchecked")
    private QueryResult tryParseTwoBlockConvention(List<McpSchema.Content> content) {
        if (content.size() < 2) return null;
        try {
            JsonNode structured = objectMapper.readTree(asText(content.get(1)));
            List<String> columns = objectMapper.convertValue(structured.get(RESULT_COLUMNS),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            List<Map<String, Object>> rows = objectMapper.convertValue(structured.get(RESULT_ROWS),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            return new QueryResult(RESULT_SOURCE_NAME, columns, rows);
        } catch (Exception e) {
            log.warn("Could not parse structured MCP result (2-block convention) for tool {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * Fallback for third-party/external MCP servers that embed tabular data as JSON text in the
     * single (only) content block, in the shape {@code {"columns":[{"name":...,"type":...}, ...],
     * "rows":[[v1,v2,...], ...]}} - as opposed to this app's own two-block convention (see
     * {@link #tryParseTwoBlockConvention(List)}). Some such servers additionally report the same
     * data via the official MCP {@code structuredContent} response field, which the MCP Java SDK
     * version currently used by this app does not yet expose - so this parses the JSON already
     * present in the text block instead, requiring no SDK upgrade.
     *
     * <p>Returns {@code null} (not tabular data) whenever the JSON doesn't have both a
     * {@code columns} array (of strings or {@code {"name":...}} objects) and a {@code rows} array
     * of same-length row arrays - e.g. {@code list_tables}'s response, which has a different
     * shape ({@code tables}/{@code count}/...) - so this never misfires on non-tabular results.
     */
    private QueryResult tryParseSingleBlockStructuredJson(List<McpSchema.Content> content) {
        if (content.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(asText(content.get(0)));
            JsonNode columnsNode = root.get(RESULT_COLUMNS);
            JsonNode rowsNode = root.get(RESULT_ROWS);
            if (columnsNode == null || !columnsNode.isArray() || rowsNode == null || !rowsNode.isArray()) {
                return null;
            }

            List<String> columns = new ArrayList<>();
            for (JsonNode col : columnsNode) {
                if (col.isTextual()) {
                    columns.add(col.asText());
                } else if (col.isObject() && col.hasNonNull("name")) {
                    columns.add(col.get("name").asText());
                } else {
                    return null; // unrecognized column shape - don't guess, bail out
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode rowNode : rowsNode) {
                if (!rowNode.isArray() || rowNode.size() != columns.size()) {
                    return null; // not a positional row array matching the column count - bail out
                }
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < columns.size(); i++) {
                    row.put(columns.get(i), objectMapper.convertValue(rowNode.get(i), Object.class));
                }
                rows.add(row);
            }
            return new QueryResult(RESULT_SOURCE_NAME, columns, rows);
        } catch (Exception e) {
            log.debug("Content block is not structured tabular JSON for tool {}: {}", name, e.getMessage());
            return null;
        }
    }

    private static String asText(McpSchema.Content c) {
        return (c instanceof McpSchema.TextContent tc) ? tc.text() : "";
    }
}

