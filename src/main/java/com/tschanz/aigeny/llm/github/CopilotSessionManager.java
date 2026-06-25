package com.tschanz.aigeny.llm.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Manages GitHub Copilot session tokens.
 * <p>
 * Exchanges a long-lived GitHub OAuth token for a short-lived Copilot session
 * token (JWT) and refreshes it automatically when it approaches expiry.
 * <p>
 * The session token is used to authenticate with the Copilot API endpoints.
 */
@Service
public class CopilotSessionManager {

    private static final Logger log = LoggerFactory.getLogger(CopilotSessionManager.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String COPILOT_TOKEN_URL = "https://api.github.com/copilot_internal/v2/token";
    private static final String EDITOR_VERSION = "vscode/1.95.0";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/0.20.0";
    private static final String USER_AGENT = "GitHubCopilotChat/0.20.0";

    private final HttpClient http;

    /** Short-lived Copilot session JWT, refreshed automatically. */
    private volatile String copilotToken;
    private volatile Instant copilotTokenExpiresAt = Instant.EPOCH;
    /** API base reported by the Copilot token endpoint (e.g. https://api.githubcopilot.com). */
    private volatile String copilotApiBase = "https://api.githubcopilot.com";

    public CopilotSessionManager() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Constructor with custom HttpClient (useful for testing).
     *
     * @param httpClient the HTTP client to use
     */
    public CopilotSessionManager(HttpClient httpClient) {
        this.http = httpClient;
    }

    /**
     * Returns a valid Copilot session token, refreshing it if needed.
     * <p>
     * Tokens are refreshed if they expire within 60 seconds.
     *
     * @param githubToken the GitHub OAuth token to exchange
     * @return a valid Copilot session token
     * @throws Exception if token refresh fails
     */
    public synchronized String getCopilotToken(String githubToken) throws Exception {
        if (githubToken == null || githubToken.isBlank()) {
            throw new IllegalArgumentException("GitHub token cannot be null or blank");
        }

        // Refresh if token is expired or expires soon
        if (copilotToken != null && Instant.now().isBefore(copilotTokenExpiresAt.minusSeconds(60))) {
            return copilotToken;
        }

        refreshCopilotToken(githubToken);
        return copilotToken;
    }

    /**
     * Returns the Copilot API base URL.
     *
     * @return the API base URL (e.g., https://api.githubcopilot.com)
     */
    public String getCopilotApiBase() {
        return copilotApiBase;
    }

    /**
     * Returns the current token expiration time.
     *
     * @return the expiration instant, or EPOCH if no token is cached
     */
    public Instant getTokenExpiresAt() {
        return copilotTokenExpiresAt;
    }

    /**
     * Checks if a valid token is currently cached.
     *
     * @return true if a token exists and is not expired
     */
    public boolean hasValidToken() {
        return copilotToken != null && Instant.now().isBefore(copilotTokenExpiresAt);
    }

    /**
     * Clears the cached session token.
     */
    public synchronized void clearToken() {
        copilotToken = null;
        copilotTokenExpiresAt = Instant.EPOCH;
        copilotApiBase = "https://api.githubcopilot.com";
        log.debug("Copilot session token cleared");
    }

    /**
     * Exchanges a GitHub OAuth token for a fresh Copilot session token.
     *
     * @param githubToken the GitHub OAuth token
     * @throws Exception if the token exchange fails
     */
    private synchronized void refreshCopilotToken(String githubToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(COPILOT_TOKEN_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "token " + githubToken)
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
        if (api != null && !api.isBlank()) {
            copilotApiBase = api.replaceAll("/$", "");
        }

        log.info("Copilot session token refreshed (api={}, valid until {})",
                copilotApiBase, copilotTokenExpiresAt);
    }
}

