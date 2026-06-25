package com.tschanz.aigeny.llm.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Handles GitHub OAuth 2.0 Device Authorization Flow.
 * <p>
 * This is the same flow that VS Code, GitHub CLI, and other official GitHub
 * clients use for device-based authentication without requiring a browser redirect.
 */
@Service
public class GitHubOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // Public GitHub Copilot OAuth client_id (the same one VS Code & OpenCode use).
    // This is a device-flow-only public client – no client secret needed.
    private static final String COPILOT_CLIENT_ID = "Iv1.b507a08c87ecfe98";
    private static final String OAUTH_SCOPE       = "read:user";

    private static final String DEVICE_CODE_URL  = "https://github.com/login/device/code";
    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_AGENT       = "GitHubCopilotChat/0.20.0";

    private final HttpClient http;

    // Active device-flow polling state (only one pairing at a time).
    private volatile DeviceFlowState pendingFlow;

    public GitHubOAuthClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Constructor with custom HttpClient (useful for testing).
     *
     * @param httpClient the HTTP client to use
     */
    public GitHubOAuthClient(HttpClient httpClient) {
        this.http = httpClient;
    }

    /**
     * Represents the result of starting a device flow.
     *
     * @param userCode        the code the user must enter
     * @param verificationUri the URL where the user enters the code
     * @param expiresIn       how long the code is valid (seconds)
     */
    public record DeviceFlowStart(String userCode, String verificationUri, int expiresIn) {}

    /**
     * Represents the result of polling for a token.
     */
    public enum PollResult {
        /** Token was successfully obtained. */
        SUCCESS,
        /** User has not yet authorized (keep waiting). */
        PENDING,
        /** GitHub requested to slow down polling. */
        SLOW_DOWN,
        /** The device code expired before authorization. */
        EXPIRED,
        /** The user denied the authorization request. */
        DENIED,
        /** An unexpected error occurred. */
        ERROR
    }

    /**
     * Starts the OAuth device flow and returns the user code for display.
     *
     * @return device flow start information
     * @throws Exception if the request fails
     */
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

        this.pendingFlow = new DeviceFlowState(deviceCode, interval, expiresIn);

        return new DeviceFlowStart(userCode, verificationUri, expiresIn);
    }

    /**
     * Polls once for the access token. Should be called repeatedly until
     * SUCCESS, EXPIRED, DENIED, or ERROR is returned.
     *
     * @return the poll result and optional token
     * @throws Exception if the request fails
     */
    public synchronized PollResultWithToken pollForToken() throws Exception {
        DeviceFlowState state = pendingFlow;
        if (state == null) {
            throw new IllegalStateException("No device flow in progress");
        }

        if (Instant.now().isAfter(state.expiresAt)) {
            log.warn("GitHub device code expired before user authorized");
            pendingFlow = null;
            return new PollResultWithToken(PollResult.EXPIRED, null);
        }

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
            log.info("GitHub OAuth token received – pairing successful");
            pendingFlow = null;
            return new PollResultWithToken(PollResult.SUCCESS, token);
        }

        String error = node.path("error").asText("");
        return switch (error) {
            case "authorization_pending" -> new PollResultWithToken(PollResult.PENDING, null);
            case "slow_down" -> {
                state.intervalSeconds += 5;
                log.debug("GitHub requested slow_down, interval now {}s", state.intervalSeconds);
                yield new PollResultWithToken(PollResult.SLOW_DOWN, null);
            }
            case "expired_token" -> {
                log.warn("GitHub device code expired");
                pendingFlow = null;
                yield new PollResultWithToken(PollResult.EXPIRED, null);
            }
            case "access_denied" -> {
                log.warn("GitHub access denied by user");
                pendingFlow = null;
                yield new PollResultWithToken(PollResult.DENIED, null);
            }
            default -> {
                log.warn("GitHub OAuth poll returned unexpected error: {}", error);
                yield new PollResultWithToken(PollResult.ERROR, null);
            }
        };
    }

    /**
     * Returns the recommended polling interval in seconds.
     *
     * @return polling interval, or 0 if no flow is active
     */
    public int getPollingIntervalSeconds() {
        DeviceFlowState state = pendingFlow;
        return state != null ? state.intervalSeconds : 0;
    }

    /**
     * Returns the expiration time of the current device flow.
     *
     * @return expiration instant, or null if no flow is active
     */
    public Instant getFlowExpiresAt() {
        DeviceFlowState state = pendingFlow;
        return state != null ? state.expiresAt : null;
    }

    /**
     * Checks if a device flow is currently pending.
     *
     * @return true if a flow is active
     */
    public boolean isFlowPending() {
        return pendingFlow != null;
    }

    /**
     * Cancels any pending device flow.
     */
    public synchronized void cancelPendingFlow() {
        pendingFlow = null;
    }

    /**
     * Result of a token poll, including the token if successful.
     *
     * @param result the poll result status
     * @param token  the OAuth token (only present if result is SUCCESS)
     */
    public record PollResultWithToken(PollResult result, String token) {}

    /**
     * Internal state for an active device flow.
     */
    private static final class DeviceFlowState {
        final String deviceCode;
        volatile int intervalSeconds;
        final Instant expiresAt;

        DeviceFlowState(String deviceCode, int intervalSeconds, int expiresIn) {
            this.deviceCode = deviceCode;
            this.intervalSeconds = Math.max(intervalSeconds, 5);
            this.expiresAt = Instant.now().plusSeconds(expiresIn);
        }
    }
}

