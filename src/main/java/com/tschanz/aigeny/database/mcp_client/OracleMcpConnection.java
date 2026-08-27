package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
import com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
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
 * Manages the lifecycle of the embedded Oracle DB MCP server subprocess and the
 * {@link McpSyncClient} connected to it.
 *
 * <p>On startup this launches {@link OracleMcpServerLauncher} as a child process and
 * communicates with it over the MCP stdio transport, exactly like a real external MCP
 * server would be used. After the {@code initialize} handshake, it also calls
 * {@code listTools()} once to discover the tools the server actually offers (name,
 * description, JSON schema) - this is the single source of truth for what gets exposed
 * to the LLM. See {@code AbstractOracleMcpTool} for the base class that reads this cache
 * and the concrete {@code *Tool} classes in this package for how each tool is exposed.
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
        if (!configValidator.isDbConfigured(dbConfig)) {
            log.info("Oracle DB not configured – MCP DB server not started.");
            return;
        }
        try {
            String javaBin = ProcessHandle.current().info().command().orElse("java");

            ServerParameters params = ServerParameters.builder(javaBin)
                    .args(buildJavaArgs())
                    .addEnvVar("AIGENY_DB_URL", nullToEmpty(dbConfig.getUrl()))
                    .addEnvVar("AIGENY_DB_USERNAME", nullToEmpty(dbConfig.getUsername()))
                    .addEnvVar("AIGENY_DB_PASSWORD", nullToEmpty(dbConfig.getPassword()))
                    .addEnvVar("AIGENY_DB_SCHEMA", nullToEmpty(dbConfig.getEffectiveSchema()))
                    .build();

            StdioClientTransport transport = new StdioClientTransport(params, objectMapper);
            McpSyncClient newClient = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("aigeny", "1.0.0"))
                    .build();
            newClient.initialize();
            this.client = newClient;
            log.info("Oracle DB MCP server started (stdio subprocess) and initialized.");

            discoverTools(newClient);
        } catch (Exception e) {
            log.error("Failed to start Oracle DB MCP server: {}", e.getMessage(), e);
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
            log.info("Oracle DB MCP server stopped.");
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

    /** Invokes a tool by name on the MCP server. Throws if not connected. */
    McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
        McpSyncClient c = this.client;
        if (c == null) {
            throw new IllegalStateException("Oracle DB MCP client is not connected");
        }
        return c.callTool(new McpSchema.CallToolRequest(name, arguments));
    }
}

