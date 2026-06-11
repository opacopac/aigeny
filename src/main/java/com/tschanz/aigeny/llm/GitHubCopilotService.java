package com.tschanz.aigeny.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Connects AIgeny to GitHub using the OAuth 2.0 Device Authorization flow –
 * the same flow OpenCode / VS Code / GitHub CLI use to pair with GitHub Copilot.
 *
 * Once paired, the long-lived GitHub OAuth token (gho_xxx) is exchanged for a
 * short-lived Copilot session token (refresh ~25 min before expiry) which can
 * then be used to call the Copilot-hosted chat completions endpoint.
 */
@Service
public class GitHubCopilotService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCopilotService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // Public GitHub Copilot OAuth client_id (the same one VS Code & OpenCode use).
    // This is a device-flow-only public client – no client secret needed.
    private static final String COPILOT_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String OAUTH_SCOPE       = "read:user";

    private static final String DEVICE_CODE_URL  = "https://github.com/login/device/code";
    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL         = "https://api.github.com/user";
    private static final String COPILOT_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";

    // Headers the Copilot backend expects (mirrors VS Code / OpenCode).
    private static final String EDITOR_VERSION       = "vscode/1.95.0";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/0.20.0";
    private static final String USER_AGENT           = "GitHubCopilotChat/0.20.0";
    private static final String COPILOT_INTEGRATION_ID = "vscode-chat";

    private final Path tokenFile = Path.of(System.getProperty("user.home"), ".aigeny", "github-copilot.json");
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** Long-lived GitHub OAuth user token (gho_xxx). */
    private final AtomicReference<String> githubToken = new AtomicReference<>();
    /** Short-lived Copilot session JWT, refreshed automatically. */
    private volatile String copilotToken;
    private volatile Instant copilotTokenExpiresAt = Instant.EPOCH;
    /** API base reported by the Copilot token endpoint (e.g. https://api.githubcopilot.com). */
    private volatile String copilotApiBase = "https://api.githubcopilot.com";
    private volatile String githubLogin; // user login, for UI display

    // Active device-flow polling state (only one pairing at a time).
    private volatile DeviceFlowState pendingFlow;

    public record DeviceFlowStart(String userCode, String verificationUri, int expiresIn) {}

    private static final class DeviceFlowState {
        final String deviceCode;
        volatile int intervalSeconds;
        final Instant expiresAt;
        volatile Thread thread;
        volatile String error;
        volatile boolean done;
        DeviceFlowState(String deviceCode, int intervalSeconds, int expiresIn) {
            this.deviceCode = deviceCode;
            this.intervalSeconds = Math.max(intervalSeconds, 5);
            this.expiresAt = Instant.now().plusSeconds(expiresIn);
        }
    }

    @PostConstruct
    public void init() {
        loadStoredToken();
        if (githubToken.get() != null) {
            log.info("GitHub Copilot: stored OAuth token found, verifying...");
            try {
                fetchGithubLogin();
                // Fire-and-forget: warm up the Copilot session + log the model list
                new Thread(this::logAvailableModelsQuietly, "copilot-models-init").start();
            } catch (Exception e) {
                log.warn("GitHub Copilot: stored token is invalid ({}). Disconnect required.", e.getMessage());
                disconnect();
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public synchronized DeviceFlowStart startDeviceFlow() throws Exception {
        // Cancel any previous attempt
        cancelPendingFlow();

        String body = "client_id=" + URLEncoder.encode(COPILOT_CLIENT_ID, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(OAUTH_SCOPE, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder(URI.create(DEVICE_CODE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("GitHub device flow init failed: HTTP "
                    + resp.statusCode() + " – " + resp.body());
        }

        JsonNode node = JSON.readTree(resp.body());
        String deviceCode      = node.path("device_code").asText();
        String userCode        = node.path("user_code").asText();
        String verificationUri = node.path("verification_uri").asText("https://github.com/login/device");
        int interval           = node.path("interval").asInt(5);
        int expiresIn          = node.path("expires_in").asInt(900);

        log.info("GitHub device flow started – user_code={} verification_uri={} expires_in={}s",
                userCode, verificationUri, expiresIn);

        DeviceFlowState state = new DeviceFlowState(deviceCode, interval, expiresIn);
        this.pendingFlow = state;
        state.thread = new Thread(() -> pollForToken(state), "github-copilot-poll");
        state.thread.setDaemon(true);
        state.thread.start();

        return new DeviceFlowStart(userCode, verificationUri, expiresIn);
    }

    /** Status object for the front end. */
    public Map<String, Object> getStatus() {
        boolean connected = githubToken.get() != null;
        DeviceFlowState f = pendingFlow;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("connected", connected);
        m.put("login", githubLogin);
        if (f != null && !f.done && f.error == null) {
            m.put("pairing", true);
            m.put("expiresAt", f.expiresAt.toString());
        } else {
            m.put("pairing", false);
        }
        if (f != null && f.error != null) m.put("lastError", f.error);
        return m;
    }

    public synchronized void disconnect() {
        cancelPendingFlow();
        githubToken.set(null);
        copilotToken = null;
        copilotTokenExpiresAt = Instant.EPOCH;
        githubLogin = null;
        try { Files.deleteIfExists(tokenFile); } catch (Exception ignored) {}
        log.info("GitHub Copilot: disconnected.");
    }

    public boolean isConnected() { return githubToken.get() != null; }

    /**
     * Returns a valid Copilot session token, refreshing it if needed.
     * Throws if the user is not connected.
     */
    public synchronized String getCopilotSessionToken() throws Exception {
        if (githubToken.get() == null) {
            throw new IllegalStateException("GitHub is not connected – open the Connect dialog first.");
        }
        if (copilotToken != null && Instant.now().isBefore(copilotTokenExpiresAt.minusSeconds(60))) {
            return copilotToken;
        }
        refreshCopilotToken();
        return copilotToken;
    }

    public String getCopilotApiBase() { return copilotApiBase; }

    public List<String> listModels() throws Exception {
        String token = getCopilotSessionToken();
        HttpRequest req = HttpRequest.newBuilder(URI.create(copilotApiBase + "/models"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Editor-Version", EDITOR_VERSION)
                .header("Editor-Plugin-Version", EDITOR_PLUGIN_VERSION)
                .header("Copilot-Integration-Id", COPILOT_INTEGRATION_ID)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Listing Copilot models failed: HTTP " + resp.statusCode()
                    + " – " + resp.body());
        }
        JsonNode root = JSON.readTree(resp.body());
        JsonNode data = root.path("data");
        List<String> ids = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode m : data) ids.add(m.path("id").asText());
        }
        return ids;
    }

    /** Common headers needed by all Copilot API calls (used by GitHubCopilotAdapter too). */
    public Map<String, String> copilotHeaders() {
        return Map.of(
                "Editor-Version",       EDITOR_VERSION,
                "Editor-Plugin-Version", EDITOR_PLUGIN_VERSION,
                "Copilot-Integration-Id", COPILOT_INTEGRATION_ID,
                "User-Agent",           USER_AGENT,
                "OpenAI-Intent",        "conversation-panel"
        );
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private void cancelPendingFlow() {
        DeviceFlowState s = pendingFlow;
        if (s != null && s.thread != null && s.thread.isAlive()) {
            s.thread.interrupt();
        }
        pendingFlow = null;
    }

    private void pollForToken(DeviceFlowState state) {
        log.info("GitHub Copilot: polling for access token every {}s (timeout {}s)...",
                state.intervalSeconds, Duration.between(Instant.now(), state.expiresAt).getSeconds());
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (Instant.now().isAfter(state.expiresAt)) {
                    state.error = "device code expired";
                    log.warn("GitHub Copilot: device code expired before user authorised.");
                    return;
                }
                Thread.sleep(state.intervalSeconds * 1000L);

                String body = "client_id=" + URLEncoder.encode(COPILOT_CLIENT_ID, StandardCharsets.UTF_8)
                        + "&device_code=" + URLEncoder.encode(state.deviceCode, StandardCharsets.UTF_8)
                        + "&grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder(URI.create(ACCESS_TOKEN_URL))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", USER_AGENT)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode node = JSON.readTree(resp.body());

                if (node.has("access_token")) {
                    String token = node.path("access_token").asText();
                    onPairingSuccess(token);
                    state.done = true;
                    return;
                }
                String error = node.path("error").asText("");
                switch (error) {
                    case "authorization_pending" -> { /* keep waiting */ }
                    case "slow_down" -> state.intervalSeconds += 5;
                    case "expired_token", "access_denied" -> {
                        state.error = error;
                        log.warn("GitHub Copilot: pairing failed ({})", error);
                        return;
                    }
                    default -> log.debug("GitHub Copilot poll: {}", resp.body());
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            state.error = e.getMessage();
            log.warn("GitHub Copilot polling error: {}", e.toString());
        }
    }

    private void onPairingSuccess(String token) {
        githubToken.set(token);
        persistToken(token);
        log.info("GitHub Copilot: OAuth token received – pairing successful.");
        try {
            fetchGithubLogin();
        } catch (Exception e) {
            log.warn("GitHub Copilot: could not fetch user info: {}", e.getMessage());
        }
        logAvailableModelsQuietly();
    }

    private void fetchGithubLogin() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(USER_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "token " + githubToken.get())
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " – " + resp.body());
        }
        JsonNode node = JSON.readTree(resp.body());
        githubLogin = node.path("login").asText(null);
        log.info("GitHub Copilot: connected as user '{}'", githubLogin);
    }

    private synchronized void refreshCopilotToken() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(COPILOT_TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "token " + githubToken.get())
                .header("Accept", "application/json")
                .header("Editor-Version", EDITOR_VERSION)
                .header("Editor-Plugin-Version", EDITOR_PLUGIN_VERSION)
                .header("User-Agent", USER_AGENT)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Copilot token exchange failed: HTTP " + resp.statusCode()
                    + " – " + resp.body());
        }
        JsonNode node = JSON.readTree(resp.body());
        copilotToken = node.path("token").asText();
        long expiresAtEpoch = node.path("expires_at").asLong(Instant.now().plusSeconds(1500).getEpochSecond());
        copilotTokenExpiresAt = Instant.ofEpochSecond(expiresAtEpoch);
        String api = node.path("endpoints").path("api").asText(null);
        if (api != null && !api.isBlank()) copilotApiBase = api.replaceAll("/$", "");
        log.info("GitHub Copilot: session token refreshed (api={}, valid until {})",
                copilotApiBase, copilotTokenExpiresAt);
    }

    private void logAvailableModelsQuietly() {
        try {
            List<String> models = listModels();
            log.info("GitHub Copilot: {} available models for user '{}': {}",
                    models.size(), githubLogin, models);
        } catch (Exception e) {
            log.warn("GitHub Copilot: could not list models – {}", e.getMessage());
        }
    }

    private void persistToken(String token) {
        try {
            Files.createDirectories(tokenFile.getParent());
            ObjectNode obj = JSON.createObjectNode();
            obj.put("access_token", token);
            obj.put("saved_at", Instant.now().toString());
            Files.writeString(tokenFile, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(obj));
            // Best-effort: restrict to owner only on POSIX systems
            try { tokenFile.toFile().setReadable(false, false); tokenFile.toFile().setReadable(true, true); }
            catch (Exception ignored) {}
            log.debug("GitHub Copilot: OAuth token persisted to {}", tokenFile);
        } catch (Exception e) {
            log.warn("GitHub Copilot: could not persist token to {}: {}", tokenFile, e.getMessage());
        }
    }

    private void loadStoredToken() {
        if (!Files.exists(tokenFile)) return;
        try {
            JsonNode n = JSON.readTree(Files.readString(tokenFile));
            String t = n.path("access_token").asText(null);
            if (t != null && !t.isBlank()) githubToken.set(t);
        } catch (Exception e) {
            log.warn("Could not read {}: {}", tokenFile, e.getMessage());
        }
    }
}


