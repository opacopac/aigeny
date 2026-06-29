package com.tschanz.aigeny.llm.github;

import com.tschanz.aigeny.llm.github.CopilotApiClient;
import com.tschanz.aigeny.llm.github.CopilotSessionManager;
import com.tschanz.aigeny.llm.github.GitHubOAuthClient;
import com.tschanz.aigeny.llm.github.GitHubTokenStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Facade service for GitHub Copilot integration.
 * <p>
 * Orchestrates OAuth authentication, token management, and Copilot API access
 * by delegating to specialized services:
 * <ul>
 *   <li>{@link GitHubOAuthClient} - OAuth Device Authorization Flow</li>
 *   <li>{@link GitHubTokenStore} - Token persistence</li>
 *   <li>{@link CopilotSessionManager} - Copilot session token refresh</li>
 *   <li>{@link CopilotApiClient} - GitHub/Copilot API calls</li>
 * </ul>
 */
@Service
public class GitHubCopilotService {

    private static final Logger log = LoggerFactory.getLogger(GitHubCopilotService.class);

    private final GitHubOAuthClient oauthClient;
    private final GitHubTokenStore tokenStore;
    private final CopilotSessionManager sessionManager;
    private final CopilotApiClient apiClient;

    /** Long-lived GitHub OAuth user token (gho_xxx). */
    private final AtomicReference<String> githubToken = new AtomicReference<>();
    /** GitHub username for UI display. */
    private volatile String githubLogin;

    // Active device-flow polling state (only one pairing at a time).
    private volatile DeviceFlowPollingState pollingState;

    public GitHubCopilotService(GitHubOAuthClient oauthClient,
                                GitHubTokenStore tokenStore,
                                CopilotSessionManager sessionManager,
                                CopilotApiClient apiClient) {
        this.oauthClient = oauthClient;
        this.tokenStore = tokenStore;
        this.sessionManager = sessionManager;
        this.apiClient = apiClient;
    }

    /**
     * Result of starting a device flow.
     *
     * @param userCode        the code to display to the user
     * @param verificationUri the URL where the user enters the code
     * @param expiresIn       validity duration in seconds
     */
    public record DeviceFlowStart(String userCode, String verificationUri, int expiresIn) {}

    @PostConstruct
    public void init() {
        tokenStore.load().ifPresent(token -> {
            githubToken.set(token);
            log.info("GitHub Copilot: stored OAuth token found, verifying...");
            try {
                githubLogin = apiClient.fetchGithubLogin(token);
                // Fire-and-forget: warm up the Copilot session + log the model list
                new Thread(this::logAvailableModelsQuietly, "copilot-models-init").start();
            } catch (Exception e) {
                log.warn("GitHub Copilot: stored token is invalid ({}). Disconnect required.", e.getMessage());
                disconnect();
            }
        });
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Starts the OAuth Device Authorization Flow.
     *
     * @return device flow start information for user display
     * @throws Exception if starting the flow fails
     */
    public synchronized DeviceFlowStart startDeviceFlow() throws Exception {
        // Cancel any previous attempt
        cancelPendingFlow();

        GitHubOAuthClient.DeviceFlowStart flowStart = oauthClient.startDeviceFlow();

        // Start background polling
        DeviceFlowPollingState state = new DeviceFlowPollingState();
        this.pollingState = state;
        state.thread = new Thread(() -> pollForToken(state), "github-copilot-poll");
        state.thread.setDaemon(true);
        state.thread.start();

        return new DeviceFlowStart(
                flowStart.userCode(),
                flowStart.verificationUri(),
                flowStart.expiresIn()
        );
    }

    /**
     * Returns the current connection status.
     *
     * @return status map for the frontend
     */
    public Map<String, Object> getStatus() {
        boolean connected = githubToken.get() != null;
        DeviceFlowPollingState state = pollingState;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("connected", connected);
        m.put("login", githubLogin);

        if (state != null && !state.done && state.error == null) {
            m.put("pairing", true);
            Instant expiresAt = oauthClient.getFlowExpiresAt();
            if (expiresAt != null) {
                m.put("expiresAt", expiresAt.toString());
            }
        } else {
            m.put("pairing", false);
        }

        if (state != null && state.error != null) {
            m.put("lastError", state.error);
        }

        return m;
    }

    /**
     * Disconnects from GitHub Copilot and clears all tokens.
     */
    public synchronized void disconnect() {
        cancelPendingFlow();
        githubToken.set(null);
        sessionManager.clearToken();
        githubLogin = null;
        tokenStore.delete();
        log.info("GitHub Copilot: disconnected");
    }

    /**
     * Checks if GitHub is connected.
     *
     * @return true if a GitHub token is available
     */
    public boolean isConnected() {
        return githubToken.get() != null;
    }

    /**
     * Returns a valid Copilot session token, refreshing it if needed.
     *
     * @return a valid Copilot session token
     * @throws Exception if not connected or token refresh fails
     */
    public synchronized String getCopilotSessionToken() throws Exception {
        String token = githubToken.get();
        if (token == null) {
            throw new IllegalStateException("GitHub is not connected – open the Connect dialog first.");
        }
        return sessionManager.getCopilotToken(token);
    }

    /**
     * Returns the Copilot API base URL.
     *
     * @return the API base URL
     */
    public String getCopilotApiBase() {
        return sessionManager.getCopilotApiBase();
    }

    /**
     * Lists available Copilot models for the authenticated user.
     *
     * @return list of model IDs
     * @throws Exception if listing fails
     */
    public List<String> listModels() throws Exception {
        String token = getCopilotSessionToken();
        String apiBase = sessionManager.getCopilotApiBase();
        return apiClient.listModels(apiBase, token);
    }

    /**
     * Returns common headers needed by all Copilot API calls.
     *
     * @return map of header names to values
     */
    public Map<String, String> copilotHeaders() {
        return apiClient.getCopilotHeaders();
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private void cancelPendingFlow() {
        DeviceFlowPollingState state = pollingState;
        if (state != null && state.thread != null && state.thread.isAlive()) {
            state.thread.interrupt();
        }
        pollingState = null;
        oauthClient.cancelPendingFlow();
    }

    private void pollForToken(DeviceFlowPollingState state) {
        int intervalSeconds = oauthClient.getPollingIntervalSeconds();
        Instant expiresAt = oauthClient.getFlowExpiresAt();

        log.info("GitHub Copilot: polling for access token every {}s (timeout {}s)...",
                intervalSeconds, Duration.between(Instant.now(), expiresAt).getSeconds());

        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(intervalSeconds * 1000L);

                GitHubOAuthClient.PollResultWithToken result = oauthClient.pollForToken();

                switch (result.result()) {
                    case SUCCESS -> {
                        onPairingSuccess(result.token());
                        state.done = true;
                        return;
                    }
                    case PENDING -> { /* keep waiting */ }
                    case SLOW_DOWN -> intervalSeconds = oauthClient.getPollingIntervalSeconds();
                    case EXPIRED, DENIED, ERROR -> {
                        state.error = result.result().name().toLowerCase();
                        log.warn("GitHub Copilot: pairing failed ({})", state.error);
                        return;
                    }
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
        tokenStore.save(token);
        log.info("GitHub Copilot: OAuth token received – pairing successful");

        try {
            githubLogin = apiClient.fetchGithubLogin(token);
        } catch (Exception e) {
            log.warn("GitHub Copilot: could not fetch user info: {}", e.getMessage());
        }

        logAvailableModelsQuietly();
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

    /**
     * Internal state for device flow polling.
     */
    private static final class DeviceFlowPollingState {
        volatile Thread thread;
        volatile String error;
        volatile boolean done;
    }
}

