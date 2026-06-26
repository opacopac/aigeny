package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.Messages;
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
import java.util.Map;

/**
 * Tool for searching Jira users by display name via REST API (Jira Server/Data Center).
 * Returns the technical username (used in JQL assignee queries) together with display name and email.
 */
@Service
public class SearchJiraUserTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(SearchJiraUserTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED  = "jira.error.not_configured";
    private static final String MSG_NO_TOKEN        = "jira.error.no_token";
    private static final String MSG_MISSING_NAME    = "jira.user.error.missing_name";
    private static final String MSG_AUTH_FAILED     = "jira.error.auth_failed_en";
    private static final String MSG_HTTP_ERROR      = "jira.error.http_en";
    private static final String MSG_NO_USERS        = "jira.user.no_users_found";
    private static final String MSG_USERS_FOUND     = "jira.user.users_found";

    private final JiraConfiguration jiraConfig;
    private final HttpClient http;

    public SearchJiraUserTool(JiraConfiguration jiraConfig) {
        this.jiraConfig = jiraConfig;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getName() { return "search_jira_user"; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = JSON.readTree(argumentsJson);
            String name = args.path("displayName").asText("").trim();
            if (!name.isBlank()) return "Jira-Benutzer suchen: " + name;
        } catch (Exception ignored) {}
        return "Jira-Benutzer suchen";
    }

    @Override
    public String getDescription() {
        return "Search for Jira users by display name (full name). " +
               "Returns the technical username ('name' field) which can be used in JQL queries " +
               "such as 'assignee = <username>'. " +
               "Example: provide displayName='Andreas Gallmann' to find the corresponding Jira account.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "displayName", Map.of(
                "type", "string",
                "description", "The display name (full name) of the Jira user to search for, e.g. 'Andreas Gallmann'"
            )
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap, "required", new String[]{"displayName"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
                String baseUrl = jiraConfig.getBaseUrl() == null ? "" : jiraConfig.getBaseUrl().replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        // Resolve effective token: ThreadLocal (set by ChatController) takes priority over server config
        String effectiveToken = JiraTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = jiraConfig.getToken();
        }

        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult(Messages.get(MSG_NO_TOKEN));
        }

        JsonNode args = JSON.readTree(argumentsJson);
        String displayName = args.path("displayName").asText("").trim();

        if (displayName.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_NAME));
        }

        String authHeader = "Bearer " + effectiveToken;

        String url = baseUrl + "/rest/api/2/user/search?username="
                + URLEncoder.encode(displayName, StandardCharsets.UTF_8);
        log.info(">> JIRA USER SEARCH  displayName=\"{}\"", displayName);
        log.info("   URL: {}", url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET().build();

        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            log.warn("   Response body: {}", response.body());
            return new ToolResult(Messages.get(MSG_AUTH_FAILED));
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA USER SEARCH status={} body={}", response.statusCode(), response.body());
            return new ToolResult(Messages.get(MSG_HTTP_ERROR, response.statusCode(), response.body()));
        }

        log.info("<< JIRA USER SEARCH status=200 bodySize={}B", response.body().length());
        return parseUserSearchResponse(response.body());
    }

    private ToolResult parseUserSearchResponse(String json) throws Exception {
        JsonNode users = JSON.readTree(json);

        if (!users.isArray() || users.isEmpty()) {
            return new ToolResult(Messages.get(MSG_NO_USERS));
        }

        log.info("   Jira user search returned {} result(s)", users.size());

        StringBuilder sb = new StringBuilder();
        sb.append(Messages.get(MSG_USERS_FOUND, users.size())).append("\n\n");
        sb.append("| Username (for JQL) | Display Name | Email | Active |\n");
        sb.append("|---|---|---|---|\n");

        for (JsonNode user : users) {
            String name        = user.path("name").asText("-");
            String displayName = user.path("displayName").asText("-");
            String email       = user.path("emailAddress").asText("-");
            boolean active     = user.path("active").asBoolean(false);

            sb.append("| `").append(name).append("` | ")
              .append(displayName).append(" | ")
              .append(email).append(" | ")
              .append(active ? "✓" : "✗").append(" |\n");
        }

        sb.append("\n_Use the **Username** value (e.g. `assignee = u128080`) in JQL queries._");

        return new ToolResult(sb.toString());
    }
}
