package com.tschanz.aigeny.llm_tool.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.config.AigenyProperties;
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
 * Executes confirmed Jira write actions (update issue fields, add comments).
 */
@Service
public class JiraWriteExecutor {

    private static final Logger log = LoggerFactory.getLogger(JiraWriteExecutor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AigenyProperties props;
    private final HttpClient http;

    public JiraWriteExecutor(AigenyProperties props) {
        this.props = props;
        this.http  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Execute the given pending action using the provided Jira token.
     * @return human-readable result message
     */
    public String execute(PendingJiraAction action, String token) throws Exception {
        String baseUrl = props.getJira().getBaseUrl().replaceAll("/$", "");
        String auth    = "Bearer " + token;

        return switch (action.getActionType()) {
            case UPDATE_ISSUE -> updateIssue(action.getIssueKey(), action.getParams(), baseUrl, auth);
            case ADD_COMMENT  -> addComment(action.getIssueKey(), action.getParams(), baseUrl, auth);
        };
    }

    private String updateIssue(String issueKey, Map<String, Object> params,
                                String baseUrl, String auth) throws Exception {
        // Build Jira update payload: { "fields": { "summary": "...", "description": "..." } }
        Map<String, Object> fields  = new java.util.LinkedHashMap<>(params);
        Map<String, Object> payload = Map.of("fields", fields);
        String body = JSON.writeValueAsString(payload);

        String url = baseUrl + "/rest/api/2/issue/" + URLEncoder.encode(issueKey, StandardCharsets.UTF_8);
        log.info(">> JIRA PUT {} body={}", url, body);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA PUT status={}", resp.statusCode());

        if (resp.statusCode() == 204 || resp.statusCode() == 200) {
            return Messages.get(Messages.JIRA_WRITE_UPDATED, issueKey);
        }
        if (resp.statusCode() == 401) {
            return Messages.get(Messages.JIRA_WRITE_AUTH_FAILED);
        }
        if (resp.statusCode() == 403) {
            return Messages.get(Messages.JIRA_WRITE_FORBIDDEN, issueKey);
        }
        return Messages.get(Messages.JIRA_WRITE_ERROR, resp.statusCode(), resp.body());
    }

    private String addComment(String issueKey, Map<String, Object> params,
                               String baseUrl, String auth) throws Exception {
        String commentText = (String) params.get("comment");
        String body = JSON.writeValueAsString(Map.of("body", commentText));

        String url = baseUrl + "/rest/api/2/issue/"
                + URLEncoder.encode(issueKey, StandardCharsets.UTF_8) + "/comment";
        log.info(">> JIRA POST comment {}", url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA POST comment status={}", resp.statusCode());

        if (resp.statusCode() == 201) {
            return Messages.get(Messages.JIRA_WRITE_COMMENT_ADDED, issueKey);
        }
        if (resp.statusCode() == 401) {
            return Messages.get(Messages.JIRA_WRITE_AUTH_FAILED);
        }
        if (resp.statusCode() == 403) {
            return Messages.get(Messages.JIRA_WRITE_FORBIDDEN, issueKey);
        }
        return Messages.get(Messages.JIRA_WRITE_ERROR, resp.statusCode(), resp.body());
    }
}

