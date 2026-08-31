package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
import com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OracleMcpConnection}.
 *
 * <p>The real {@link McpSyncClient} is normally created in {@code start()} by spawning a
 * child process - not something we want to do in a fast unit test. Instead, a mocked
 * {@link McpSyncClient} is injected directly into the private {@code client} field via
 * reflection, so tool discovery and delegation can be tested in isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OracleMcpConnection")
class OracleMcpConnectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private DbConfiguration dbConfig;
    @Mock private ConfigurationValidator configValidator;
    @Mock private McpSyncClient mcpClient;

    private OracleMcpConnection connection;

    @BeforeEach
    void setUp() {
        connection = new OracleMcpConnection(dbConfig, configValidator, objectMapper);
    }

    private void injectMockClient() throws Exception {
        Field field = OracleMcpConnection.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(connection, mcpClient);
    }

    private void invokeStart() throws Exception {
        Method method = OracleMcpConnection.class.getDeclaredMethod("start");
        method.setAccessible(true);
        method.invoke(connection);
    }

    private Object readClientField() throws Exception {
        Field field = OracleMcpConnection.class.getDeclaredField("client");
        field.setAccessible(true);
        return field.get(connection);
    }

    // ── start() lifecycle ────────────────────────────────────────────────────

    @Nested
    @DisplayName("start()")
    class StartLifecycle {

        @Test
        @DisplayName("does not attempt to launch the MCP server when DB is not configured and no remote URL is set")
        void skipsStartupWhenNotConfigured() throws Exception {
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(false);
            when(dbConfig.getMcpServerUrl()).thenReturn("");

            invokeStart();

            assertThat(readClientField()).isNull();
            assertThat(connection.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("does not skip startup when a remote MCP server URL is configured, even if local DB fields are blank")
        void doesNotSkipStartupWhenRemoteUrlConfigured() {
            when(dbConfig.getMcpServerUrl()).thenReturn("http://mcp-host:8081");
            // isDbConfigured() is deliberately never stubbed here (Mockito default: false) -
            // shouldSkipStartup() must still be false because a remote URL is set. Verified
            // directly (no real connection attempt / network call involved).
            assertThat(connection.shouldSkipStartup()).isFalse();
        }

        @Test
        @DisplayName("skips startup when neither a remote URL nor local DB fields are configured")
        void skipsStartupWhenNeitherConfigured() {
            when(dbConfig.getMcpServerUrl()).thenReturn("");
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(false);

            assertThat(connection.shouldSkipStartup()).isTrue();
        }

        @Test
        @DisplayName("does not skip startup when local DB fields are configured, even without a remote URL")
        void doesNotSkipStartupWhenLocallyConfigured() {
            when(dbConfig.getMcpServerUrl()).thenReturn("");
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(true);

            assertThat(connection.shouldSkipStartup()).isFalse();
        }
    }

    // ── isRemoteConfigured() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("isRemoteConfigured()")
    class IsRemoteConfigured {

        @Test
        @DisplayName("is false when mcpServerUrl is blank")
        void falseWhenBlank() {
            when(dbConfig.getMcpServerUrl()).thenReturn("");
            assertThat(connection.isRemoteConfigured()).isFalse();
        }

        @Test
        @DisplayName("is false when mcpServerUrl is null")
        void falseWhenNull() {
            when(dbConfig.getMcpServerUrl()).thenReturn(null);
            assertThat(connection.isRemoteConfigured()).isFalse();
        }

        @Test
        @DisplayName("is true when mcpServerUrl is set")
        void trueWhenSet() {
            when(dbConfig.getMcpServerUrl()).thenReturn("http://mcp-host:8081");
            assertThat(connection.isRemoteConfigured()).isTrue();
        }
    }

    // ── buildTransport() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildTransport()")
    class BuildTransport {

        @Test
        @DisplayName("builds a StdioClientTransport (local subprocess) when no remote URL is configured")
        void buildsStdioTransportByDefault() {
            when(dbConfig.getMcpServerUrl()).thenReturn("");

            McpClientTransport transport = connection.buildTransport();

            assertThat(transport).isInstanceOf(StdioClientTransport.class);
        }

        @Test
        @DisplayName("builds an HttpClientStreamableHttpTransport (remote) when a mcpServerUrl is configured")
        void buildsStreamableTransportWhenRemoteConfigured() {
            when(dbConfig.getMcpServerUrl()).thenReturn("http://mcp-host:8081");

            McpClientTransport transport = connection.buildTransport();

            assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
        }

        @Test
        @DisplayName("still builds a valid remote transport when extra headers are configured")
        void buildsStreamableTransportWithExtraHeaders() {
            when(dbConfig.getMcpServerUrl()).thenReturn("http://mcp-host:8081");
            when(dbConfig.getMcpServerHeaders()).thenReturn(Map.of("X-API-Key", "secret-value"));

            McpClientTransport transport = connection.buildTransport();

            assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
        }

        @Test
        @DisplayName("still builds a valid remote transport when no headers are configured (null or empty map)")
        void buildsStreamableTransportWithoutHeaders() {
            when(dbConfig.getMcpServerUrl()).thenReturn("http://mcp-host:8081");
            when(dbConfig.getMcpServerHeaders()).thenReturn(null);

            McpClientTransport transport = connection.buildTransport();

            assertThat(transport).isInstanceOf(HttpClientStreamableHttpTransport.class);
        }
    }

    // ── buildJavaArgs() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildJavaArgs()")
    class BuildJavaArgs {

        @Test
        @DisplayName("uses plain -cp <classpath> <MainClass> outside a Spring Boot fat JAR (e.g. under the test JVM)")
        void usesPlainClasspathUnderTest() {
            String[] args = connection.buildJavaArgs();

            assertThat(args).hasSize(3);
            assertThat(args[0]).isEqualTo("-cp");
            assertThat(args[1]).isEqualTo(System.getProperty("java.class.path"));
            assertThat(args[2]).isEqualTo(OracleMcpServerLauncher.class.getName());
        }
    }

    // ── isAvailable() / getToolInfo() / callTool() before connecting ───────────

    @Nested
    @DisplayName("before a client is connected")
    class NotConnected {

        @Test
        @DisplayName("isAvailable() is false")
        void notAvailable() {
            assertThat(connection.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("getToolInfo() is empty for any name")
        void noToolInfo() {
            assertThat(connection.getToolInfo("list_tables")).isEmpty();
        }

        @Test
        @DisplayName("getDiscoveredToolNames() is empty")
        void noDiscoveredNames() {
            assertThat(connection.getDiscoveredToolNames()).isEmpty();
        }

        @Test
        @DisplayName("callTool() throws IllegalStateException")
        void callToolThrows() {
            assertThatThrownBy(() -> connection.callTool("list_tables", Map.of()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ── discoverTools() via start() ─────────────────────────────────────────────

    @Nested
    @DisplayName("tool discovery")
    class ToolDiscovery {

        @Test
        @DisplayName("getToolInfo() returns metadata cached from listTools() after connecting")
        void cachesDiscoveredTools() throws Exception {
            injectMockClient();
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name("list_tables")
                    .description("List all tables")
                    .inputSchema(new McpSchema.JsonSchema("object", Map.of("prefix", Map.of("type", "string")), List.of(), null, null, null))
                    .build();
            when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));

            Method discover = OracleMcpConnection.class.getDeclaredMethod("discoverTools", McpSyncClient.class);
            discover.setAccessible(true);
            discover.invoke(connection, mcpClient);

            Optional<McpSchema.Tool> info = connection.getToolInfo("list_tables");
            assertThat(info).isPresent();
            assertThat(info.get().description()).isEqualTo("List all tables");
            assertThat(connection.getDiscoveredToolNames()).containsExactly("list_tables");
        }

        @Test
        @DisplayName("connection stays usable (empty tool cache) if listTools() fails")
        void gracefullyHandlesListToolsFailure() throws Exception {
            injectMockClient();
            when(mcpClient.listTools()).thenThrow(new RuntimeException("boom"));

            Method discover = OracleMcpConnection.class.getDeclaredMethod("discoverTools", McpSyncClient.class);
            discover.setAccessible(true);
            discover.invoke(connection, mcpClient);

            assertThat(connection.getToolInfo("list_tables")).isEmpty();
            assertThat(connection.isAvailable()).isTrue();
        }
    }

    // ── callTool() delegation ───────────────────────────────────────────────────

    @Nested
    @DisplayName("callTool()")
    class CallTool {

        @BeforeEach
        void arrange() throws Exception {
            injectMockClient();
        }

        @Test
        @DisplayName("builds a CallToolRequest with the given name and arguments")
        void delegatesToClient() {
            when(mcpClient.callTool(any())).thenReturn(new McpSchema.CallToolResult("ok", false));

            McpSchema.CallToolResult result = connection.callTool("run_query", Map.of("sql", "SELECT 1"));

            assertThat(result.content()).isNotEmpty();
            verify(mcpClient).callTool(argThat(req ->
                    req.name().equals("run_query") && req.arguments().get("sql").equals("SELECT 1")));
        }
    }
}

