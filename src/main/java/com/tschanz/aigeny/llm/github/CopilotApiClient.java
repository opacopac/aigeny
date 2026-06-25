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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client for GitHub and Copilot API endpoints.
 * <p>
 * Provides methods to fetch user information from GitHub and list available
 * Copilot models.
 */
@Service
public class CopilotApiClient {

    private static final Logger log = LoggerFactory.getLogger(CopilotApiClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String USER_URL = "https://api.github.com/user";
    private static final String EDITOR_VERSION = "vscode/1.95.0";
    private static final String EDITOR_PLUGIN_VERSION = "copilot-chat/0.20.0";
    private static final String COPILOT_INTEGRATION_ID = "vscode-chat";
    private static final String USER_AGENT = "GitHubCopilotChat/0.20.0";

    private final HttpClient http;

    public CopilotApiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Constructor with custom HttpClient (useful for testing).
     *
     * @param httpClient the HTTP client to use
     */
    public CopilotApiClient(HttpClient httpClient) {
        this.http = httpClient;
    }

    /**
     * Fetches the GitHub user's login name.
     *
     * @param githubToken the GitHub OAuth token
     * @return the user's login name
     * @throws Exception if the request fails
     */
    public String fetchGithubLogin(String githubToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(USER_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "token " + githubToken)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET().build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Fetch GitHub user failed: HTTP " + resp.statusCode()
                    + " – " + resp.body());
        }

        JsonNode node = JSON.readTree(resp.body());
        String login = node.path("login").asText(null);
        if (login == null || login.isBlank()) {
            throw new RuntimeException("GitHub user response missing 'login' field");
        }

        log.info("GitHub user fetched: '{}'", login);
        return login;
    }

    /**
     * Lists available Copilot models for the authenticated user.
     *
     * @param copilotApiBase the Copilot API base URL
     * @param copilotToken   the Copilot session token
     * @return list of model IDs
     * @throws Exception if the request fails
     */
    public List<String> listModels(String copilotApiBase, String copilotToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(copilotApiBase + "/models"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + copilotToken)
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
            for (JsonNode m : data) {
                ids.add(m.path("id").asText());
            }
        }

        log.debug("Fetched {} Copilot models", ids.size());
        return ids;
    }

    /**
     * Returns common headers needed by all Copilot API calls.
     * <p>
     * These headers should be included in all requests to the Copilot chat
     * completions endpoint.
     *
     * @return map of header names to values
     */
    public Map<String, String> getCopilotHeaders() {
        return Map.of(
                "Editor-Version", EDITOR_VERSION,
                "Editor-Plugin-Version", EDITOR_PLUGIN_VERSION,
                "Copilot-Integration-Id", COPILOT_INTEGRATION_ID,
                "User-Agent", USER_AGENT,
                "OpenAI-Intent", "conversation-panel"
        );
    }
}

