package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
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
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the lifecycle of the {@link McpSyncClient} connected to the Oracle DB MCP server.
 *
 * <p>By default (no {@link DbConfiguration#getMcpServerUrl()} configured) this launches
 * {@link OracleMcpServerLauncher} as a local child process and communicates with it over
 * the MCP stdio transport, exactly like a real external MCP server would be used. When a
 * {@code mcp-server-url} is configured instead, the local subprocess is skipped entirely and
 * the client connects to that URL over the Streamable HTTP MCP transport (optionally sending
 * {@link DbConfiguration#getMcpServerHeaders()} with every request, e.g. an auth header/API
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

    private final DbConfiguration dbConfig;
    private final ConfigurationValidator configValidator;
    private final ObjectMapper objectMapper;

    private volatile McpSyncClient client;
    private volatile Map<String, McpSchema.Tool> discoveredTools = Map.of();

    public OracleMcpConnection(DbConfiguration dbConfig, ConfigurationValidator configValidator,
                                ObjectMapper objectMapper) {
        this.dbConfig = dbConfig;
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
                log.info("Oracle DB MCP client connected to remote server at {}.", dbConfig.getMcpServerUrl());
            } else {
                log.info("Oracle DB MCP server started (stdio subprocess) and initialized.");
            }

            discoverTools(newClient);
        } catch (Exception e) {
            log.error("Failed to {} Oracle DB MCP {}: {}", remote ? "connect to" : "start",
                    remote ? "server at " + dbConfig.getMcpServerUrl() : "server", e.getMessage(), e);
        }
    }

    /**
     * True when a {@link DbConfiguration#getMcpServerUrl()} is configured, i.e. tool calls
     * should go to that remote MCP server instead of a locally spawned subprocess.
     */
    boolean isRemoteConfigured() {
        String url = dbConfig.getMcpServerUrl();
        return url != null && !url.isBlank();
    }

    /**
     * Startup is only skipped when neither a remote MCP server URL nor the local JDBC
     * connection details are configured - a remote server manages its own DB credentials,
     * so it doesn't need the local {@code url}/{@code username} to be set. Package-private
     * (not {@code private}) so it can be unit-tested directly without a real connection attempt.
     */
    boolean shouldSkipStartup() {
        return !isRemoteConfigured() && !configValidator.isDbConfigured(dbConfig);
    }

    /**
     * Builds the {@link McpClientTransport} to connect with: the Streamable HTTP MCP
     * transport pointed at {@link DbConfiguration#getMcpServerUrl()} when configured
     * (with any {@link DbConfiguration#getMcpServerHeaders()} attached to every request,
     * e.g. an auth header/API key), otherwise the stdio transport to a locally spawned
     * {@link OracleMcpServerLauncher} subprocess. Package-private (not {@code private})
     * so it can be unit-tested directly.
     */
    McpClientTransport buildTransport() {
        if (isRemoteConfigured()) {
            HttpClientStreamableHttpTransport.Builder builder =
                    HttpClientStreamableHttpTransport.builder(dbConfig.getMcpServerUrl());
            Map<String, String> headers = dbConfig.getMcpServerHeaders();
            if (headers != null && !headers.isEmpty()) {
                builder.customizeRequest(req -> headers.forEach(req::header));
            }
            return builder.build();
        }

        String javaBin = ProcessHandle.current().info().command().orElse("java");
        ServerParameters params = ServerParameters.builder(javaBin)
                .args(buildJavaArgs())
                .addEnvVar("AIGENY_DB_URL", nullToEmpty(dbConfig.getUrl()))
                .addEnvVar("AIGENY_DB_USERNAME", nullToEmpty(dbConfig.getUsername()))
                .addEnvVar("AIGENY_DB_PASSWORD", nullToEmpty(dbConfig.getPassword()))
                .addEnvVar("AIGENY_DB_SCHEMA", nullToEmpty(dbConfig.getEffectiveSchema()))
                .build();
        return new StdioClientTransport(params, new JacksonMcpJsonMapper(objectMapper));
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
}

