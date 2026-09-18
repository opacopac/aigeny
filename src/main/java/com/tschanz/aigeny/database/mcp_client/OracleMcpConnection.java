package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbMcpConfiguration;
import com.tschanz.aigeny.database.DbServerConfiguration;
import com.tschanz.aigeny.database.DbServerStage;
import com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the lifecycle of the {@link McpSyncClient} connected to the Oracle DB MCP server.
 *
 * <p>By default (no {@link DbMcpConfiguration#getMcpServerUrl()} configured) this launches
 * {@link OracleMcpServerLauncher} as a local child process and communicates with it over
 * the MCP stdio transport, exactly like a real external MCP server would be used. When a
 * {@code mcp-server-url} is configured instead, the local subprocess is skipped entirely and
 * the client connects to that URL over the Streamable HTTP MCP transport (optionally sending
 * {@link DbMcpConfiguration#getMcpServerHeaders()} with every request, e.g. an auth header/API
 * key) - so switching from the embedded implementation to an independently deployed/remote
 * MCP server is a pure configuration change (see {@code aigeny.db.mcp-server-url} in
 * {@code application.yml}).
 *
 * <p>After the {@code initialize} handshake (either way), this also calls {@code listTools()}
 * once to discover the tools the server actually offers (name, description, JSON schema) -
 * this is the single source of truth for what gets exposed to the LLM. See
 * {@link GenericOracleMcpTool} and {@link OracleMcpToolProvider} for how each tool is exposed.
 */
@Service
public class OracleMcpConnection {

    private static final Logger log = LoggerFactory.getLogger(OracleMcpConnection.class);

    private final DbServerConfiguration dbServerConfig;
    private final DbMcpConfiguration dbMcpConfig;
    private final ConfigurationValidator configValidator;
    private final ObjectMapper objectMapper;

    private volatile McpSyncClient client;
    private volatile Map<String, McpSchema.Tool> discoveredTools = Map.of();

    public OracleMcpConnection(@Qualifier("dbServerConfiguration") DbServerConfiguration dbServerConfig,
                                @Qualifier("dbMcpConfiguration") DbMcpConfiguration dbMcpConfig,
                                ConfigurationValidator configValidator, ObjectMapper objectMapper) {
        this.dbServerConfig = dbServerConfig;
        this.dbMcpConfig = dbMcpConfig;
        this.configValidator = configValidator;
        this.objectMapper = objectMapper;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    void start() {
        if (shouldSkipStartup()) {
            log.info("Oracle DB not configured – MCP DB server not started.");
            return;
        }
        boolean remote = isRemoteConfigured();
        try {
            McpClientTransport transport = buildTransport();
            McpSyncClient newClient = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("aigeny", "1.0.0"))
                    .build();
            newClient.initialize();
            this.client = newClient;
            if (remote) {
                log.info("Oracle DB MCP client connected to remote server at {}.", dbMcpConfig.getMcpServerUrl());
            } else {
                log.info("Oracle DB MCP server started (stdio subprocess) and initialized.");
            }

            discoverTools(newClient);
        } catch (Exception e) {
            log.error("Failed to {} Oracle DB MCP {}: {}", remote ? "connect to" : "start",
                    remote ? "server at " + dbMcpConfig.getMcpServerUrl() : "server", e.getMessage(), e);
        }
    }

    /**
     * True when a {@link DbMcpConfiguration#getMcpServerUrl()} is configured, i.e. tool calls
     * should go to that remote MCP server instead of a locally spawned subprocess.
     */
    boolean isRemoteConfigured() {
        String url = dbMcpConfig.getMcpServerUrl();
        return url != null && !url.isBlank();
    }

    /**
     * Startup is only skipped when neither a remote MCP server URL nor the local JDBC
     * connection details are configured - a remote server manages its own DB credentials,
     * so it doesn't need the local {@code url}/{@code username} to be set. Package-private
     * (not {@code private}) so it can be unit-tested directly without a real connection attempt.
     */
    boolean shouldSkipStartup() {
        return !isRemoteConfigured() && !configValidator.isDbConfigured(dbServerConfig, dbMcpConfig);
    }

    /**
     * Builds the {@link McpClientTransport} to connect with: the Streamable HTTP MCP
     * transport pointed at {@link DbMcpConfiguration#getMcpServerUrl()} when configured
     * (with any {@link DbMcpConfiguration#getMcpServerHeaders()} attached to every request,
     * e.g. an auth header/API key), otherwise the stdio transport to a locally spawned
     * {@link OracleMcpServerLauncher} subprocess. Package-private (not {@code private})
     * so it can be unit-tested directly.
     */
    McpClientTransport buildTransport() {
        if (isRemoteConfigured()) {
            HttpClientStreamableHttpTransport.Builder builder =
                    HttpClientStreamableHttpTransport.builder(dbMcpConfig.getMcpServerUrl());
            Map<String, String> headers = dbMcpConfig.getMcpServerHeaders();
            if (headers != null && !headers.isEmpty()) {
                builder.customizeRequest(req -> headers.forEach(req::header));
            }
            return builder.build();
        }

        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ServerParameters params = ServerParameters.builder(javaBin)
                .args(buildJavaArgs())
                .addEnvVar("AIGENY_DB_STAGES_JSON", buildStagesJson())
                .addEnvVar("AIGENY_DB_DEFAULT_CONTEXT", nullToEmpty(dbMcpConfig.getDefaultContext()))
                .addEnvVar("AIGENY_DB_DEFAULT_STAGE", nullToEmpty(dbMcpConfig.getDefaultStage()))
                .build();
        return new StdioClientTransport(params, new JacksonMcpJsonMapper(objectMapper));
    }

    /**
     * Serializes all configured {@link DbServerStage}s (url/username/password/effective
     * schema, keyed by upper-cased stage name) into a single JSON blob, passed to the
     * {@link OracleMcpServerLauncher} subprocess via the {@code AIGENY_DB_STAGES_JSON} env var -
     * this keeps the set of supported stages fully open-ended (not hardcoded to INTE/PROD) on
     * both sides. Package-private (not {@code private}) so it can be unit-tested directly.
     */
    String buildStagesJson() {
        Map<String, ? extends DbServerStage> stages = dbServerConfig.getStages();
        Map<String, Object> payload = new LinkedHashMap<>();
        if (stages != null) {
            for (Map.Entry<String, ? extends DbServerStage> entry : stages.entrySet()) {
                DbServerStage stage = entry.getValue();
                Map<String, String> stagePayload = new LinkedHashMap<>();
                stagePayload.put("url", nullToEmpty(stage.getUrl()));
                stagePayload.put("username", nullToEmpty(stage.getUsername()));
                stagePayload.put("password", nullToEmpty(stage.getPassword()));
                stagePayload.put("schema", nullToEmpty(stage.getEffectiveSchema()));
                payload.put(entry.getKey().toUpperCase(), stagePayload);
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Could not serialize DB stage configuration for MCP subprocess: {}", e.getMessage());
            return "{}";
        }
    }

    private void discoverTools(McpSyncClient c) {
        try {
            List<McpSchema.Tool> tools = c.listTools().tools();
            Map<String, McpSchema.Tool> byName = new LinkedHashMap<>();
            for (McpSchema.Tool tool : tools) {
                byName.put(tool.name(), tool);
            }
            this.discoveredTools = Map.copyOf(byName);
            log.info("Discovered {} MCP tool(s) from Oracle DB server: {}", byName.size(), byName.keySet());
        } catch (Exception e) {
            log.warn("Could not list tools from Oracle DB MCP server: {}", e.getMessage());
        }
    }

    @PreDestroy
    void stop() {
        McpSyncClient c = this.client;
        if (c != null) {
            c.closeGracefully();
            log.info("Oracle DB MCP client stopped.");
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * Builds the {@code java ...} arguments used to launch {@link OracleMcpServerLauncher}
     * as a child process.
     *
     * <p>Two scenarios need to be handled:
     * <ul>
     *   <li><b>IDE / {@code mvn spring-boot:run}</b>: {@code java.class.path} is a normal,
     *       {@code File.pathSeparator}-delimited list of classes/jars - a plain
     *       {@code -cp <classpath> <MainClass>} works.</li>
     *   <li><b>Packaged executable Spring Boot fat JAR</b> (e.g. {@code aigeny-1.0.0.jar}):
     *       {@code java.class.path} is just that single jar. Its dependencies live nested
     *       under {@code BOOT-INF/lib/} and are only visible through Spring Boot's own
     *       launcher class loader - a plain {@code -cp} does <em>not</em> see them. In this
     *       case we re-use the same jar but let Spring Boot's {@code PropertiesLauncher}
     *       set up the real classpath, redirecting its entry point to our MCP server main
     *       class via the {@code loader.main} system property.</li>
     * </ul>
     *
     * <p>Package-private (not {@code private}) so it can be unit-tested directly.
     */
    String[] buildJavaArgs() {
        String classpath = System.getProperty("java.class.path");
        boolean fatJar = classpath != null
                && !classpath.contains(File.pathSeparator)
                && classpath.endsWith(".jar")
                && getClass().getClassLoader().getClass().getName().startsWith("org.springframework.boot.loader");

        if (fatJar) {
            return new String[] {
                    "-Dloader.main=" + OracleMcpServerLauncher.class.getName(),
                    "-cp", classpath,
                    "org.springframework.boot.loader.launch.PropertiesLauncher"
            };
        }
        return new String[] { "-cp", classpath, OracleMcpServerLauncher.class.getName() };
    }

    // ── Access for AbstractOracleMcpTool / concrete *Tool classes ────────────

    /** Returns true if the MCP server subprocess is up and the client is initialized. */
    boolean isAvailable() {
        return client != null;
    }

    /** Returns the server-declared metadata (description + JSON schema) for a tool, if known. */
    Optional<McpSchema.Tool> getToolInfo(String name) {
        return Optional.ofNullable(discoveredTools.get(name));
    }

    /**
     * Returns the names of all tools discovered from the server's {@code listTools()}
     * response (empty if not connected yet, or discovery failed). This is the single
     * source of truth {@link OracleMcpToolProvider} uses to build the set of client-side
     * {@link GenericOracleMcpTool} instances - nothing is hardcoded here.
     */
    List<String> getDiscoveredToolNames() {
        return List.copyOf(discoveredTools.keySet());
    }

    /** Invokes a tool by name on the MCP server. Throws if not connected. */
    McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
        McpSyncClient c = this.client;
        if (c == null) {
            throw new IllegalStateException("Oracle DB MCP client is not connected");
        }
        return c.callTool(new McpSchema.CallToolRequest(name, arguments));
    }

    // ── Sidebar "MCP" status (used by StatusAggregatorService) ──────────────

    /** Well-known name of the tool used to check whether the MCP connection is alive. */
    private static final String LIST_TABLES_TOOL = "list_tables";

    /**
     * Result of a {@link #checkListTables(String, String)} probe.
     *
     * @param connected  true if the MCP client is connected to the server
     * @param available  true if the server actually exposes a {@code list_tables} tool
     * @param tableCount number of tables reported by the last successful {@code list_tables}
     *                   call, or {@code null} if it couldn't be determined
     * @param error      error message of the last failed attempt, or {@code null} if none
     */
    public record McpListTablesStatus(boolean connected, boolean available, Integer tableCount, String error) {}

    /**
     * Checks whether the MCP connection is alive by actually invoking the {@code list_tables}
     * tool and counting the rows it returns. Used to power the sidebar "MCP" status and table
     * count instead of a direct JDBC connection to the DB.
     *
     * <p>Explicitly passes the given {@code context}/{@code stage} arguments (the caller
     * resolves these via {@code DataContextSelectionService}, so the status shown always
     * matches the context/stage actually used by real tool calls - today effectively fixed
     * config defaults, but ready for a future per-session selection), even though the
     * embedded server falls back to its own configured defaults when they're omitted (see
     * {@code ContextStageSupport}) - both are declared {@code required} in the tool's JSON
     * schema (see {@link com.tschanz.aigeny.database.mcp_server.ListTablesHandler}), and a
     * strictly-validating external/remote MCP server (see
     * {@link DbMcpConfiguration#getMcpServerUrl()}) may reject the call with a "Missing required
     * argument 'context'" style error if they're missing on the wire, unlike our lenient
     * embedded implementation.
     */
    public McpListTablesStatus checkListTables(String context, String stage) {
        if (!isAvailable()) {
            return new McpListTablesStatus(false, false, null, "MCP client is not connected");
        }
        if (getToolInfo(LIST_TABLES_TOOL).isEmpty()) {
            return new McpListTablesStatus(true, false, null, null);
        }
        try {
            McpSchema.CallToolResult result = callTool(LIST_TABLES_TOOL, listTablesArguments(context, stage));
            if (Boolean.TRUE.equals(result.isError())) {
                return new McpListTablesStatus(true, true, null, firstText(result.content()));
            }
            return new McpListTablesStatus(true, true, extractRowCount(result.content()), null);
        } catch (Exception e) {
            log.warn("Could not call {} to check MCP status: {}", LIST_TABLES_TOOL, e.getMessage());
            return new McpListTablesStatus(true, true, null, e.getMessage());
        }
    }

    /**
     * Builds the arguments for the {@link #checkListTables(String, String)} probe call: always
     * includes the given {@code context}/{@code stage} (when set), since both are declared
     * {@code required} in the tool's JSON schema and a strictly-validating external/remote MCP
     * server rejects the call with a "Missing required argument 'context'" style error if they're
     * missing on the wire - even though our lenient embedded implementation would fall back to
     * its own defaults if they were omitted. Only actually blank/unconfigured values are left out,
     * since there's nothing meaningful to send for them.
     */
    private Map<String, Object> listTablesArguments(String context, String stage) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        putIfNotBlank(arguments, "context", context);
        putIfNotBlank(arguments, "stage", stage);
        return arguments;
    }

    private static void putIfNotBlank(Map<String, Object> arguments, String key, String value) {
        if (value != null && !value.isBlank()) {
            arguments.put(key, value);
        }
    }

    /**
     * Extracts the number of tables reported by a {@code list_tables} call. Tries, in order:
     * <ol>
     *   <li>this app's own "text + structured JSON" convention (see
     *       {@code OracleSqlSupport#runSelect}) - a second content block whose text is a JSON
     *       object with a {@code rows} array - used by the embedded local MCP server;</li>
     *   <li>a text-only fallback (see {@link #extractRowCountFromText(List)}) for third-party/
     *       external MCP servers, which generally only return a single human-readable text
     *       content block and don't follow that (purely internal) two-block convention at all -
     *       without this, the sidebar "Tabellen" status would always show an error for any
     *       external server even though the {@code list_tables} tool call itself works fine
     *       (chat/tool-use only ever reads the first text block, never this row count).</li>
     * </ol>
     */
    private Integer extractRowCount(List<McpSchema.Content> content) {
        Integer structured = extractStructuredRowCount(content);
        if (structured != null) return structured;
        return extractRowCountFromText(content);
    }

    /** Extracts the row count from the structured (second) content block of a tool result, if present. */
    private Integer extractStructuredRowCount(List<McpSchema.Content> content) {
        if (content.size() < 2) return null;
        try {
            JsonNode structured = objectMapper.readTree(firstText(List.of(content.get(1))));
            JsonNode rows = structured.get("rows");
            return (rows != null && rows.isArray()) ? rows.size() : null;
        } catch (Exception e) {
            log.warn("Could not parse {} result while checking MCP status: {}", LIST_TABLES_TOOL, e.getMessage());
            return null;
        }
    }

    /**
     * Fallback row-count extraction for MCP servers that only return a single, human-readable
     * text content block (i.e. essentially every external/third-party MCP server, since the
     * two-block convention checked by {@link #extractStructuredRowCount(List)} is purely internal
     * to this app's own embedded server). Tries, in order:
     * <ol>
     *   <li>parsing the text as JSON and counting a top-level array, or a {@code rows}/{@code
     *       tables}/{@code items}/{@code results} array nested in a JSON object - common for
     *       servers that emit structured data as plain text;</li>
     *   <li>counting non-empty lines, skipping this app's own "column | column" header +
     *       "----" separator line and any trailing "... (N more rows)" summary line, if present
     *       (so a single content block from this app's own server is still handled correctly);
     *       otherwise every non-empty line is assumed to be one table (e.g. plain
     *       newline-separated table names, or a simple markdown/CSV-ish table listing).</li>
     * </ol>
     */
    private Integer extractRowCountFromText(List<McpSchema.Content> content) {
        if (content.isEmpty()) return null;
        String text = firstText(content).trim();
        if (text.isEmpty()) return null;
        if (text.equalsIgnoreCase("(no rows returned)")) return 0;

        Integer fromJson = tryCountFromJson(text);
        if (fromJson != null) return fromJson;

        List<String> lines = new java.util.ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (!line.isBlank()) lines.add(line.trim());
        }
        if (lines.isEmpty()) return null;
        if (lines.size() >= 2 && !lines.get(1).isEmpty() && lines.get(1).chars().allMatch(c -> c == '-')) {
            lines = lines.subList(2, lines.size());
        }
        if (!lines.isEmpty() && lines.get(lines.size() - 1).startsWith("...")) {
            lines = lines.subList(0, lines.size() - 1);
        }
        return lines.isEmpty() ? null : lines.size();
    }

    /** Tries to parse {@code text} as JSON and count a top-level array or a known nested array field. */
    private Integer tryCountFromJson(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isArray()) return node.size();
            if (node.isObject()) {
                for (String key : List.of("rows", "tables", "items", "results")) {
                    JsonNode arr = node.get(key);
                    if (arr != null && arr.isArray()) return arr.size();
                }
            }
        } catch (Exception ignored) {
            // Not JSON - fall through to the line-counting heuristic.
        }
        return null;
    }

    private static String firstText(List<McpSchema.Content> content) {
        if (content.isEmpty()) return "";
        McpSchema.Content c = content.get(0);
        return (c instanceof McpSchema.TextContent tc) ? tc.text() : "";
    }
}


