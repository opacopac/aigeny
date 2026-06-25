package com.tschanz.aigeny.llm_tool.jira.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jira operation for updating issue fields.
 */
@Component
public class UpdateIssueOperation implements JiraOperation {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MSG_UPDATED     = "jira.write.updated";
    private static final String MSG_AUTH_FAILED = "jira.write.auth_failed";
    private static final String MSG_FORBIDDEN   = "jira.write.forbidden";
    private static final String MSG_ERROR       = "jira.write.error";

    private final JiraHttpClient httpClient;

    public UpdateIssueOperation(JiraHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String execute(PendingJiraAction action, String baseUrl, String token) throws Exception {
        String issueKey = action.getIssueKey();
        Map<String, Object> params = action.getParams();

        Map<String, Object> fields = new LinkedHashMap<>(params);
        Map<String, Object> payload = Map.of("fields", fields);
        String body = JSON.writeValueAsString(payload);

        String url = baseUrl + "/rest/api/2/issue/" + URLEncoder.encode(issueKey, StandardCharsets.UTF_8);
        String authHeader = "Bearer " + token;

        HttpResponse<String> response = httpClient.put(url, body, authHeader);

        return switch (response.statusCode()) {
            case 200, 204 -> Messages.get(MSG_UPDATED, issueKey);
            case 401 -> Messages.get(MSG_AUTH_FAILED);
            case 403 -> Messages.get(MSG_FORBIDDEN, issueKey);
            default -> Messages.get(MSG_ERROR, response.statusCode(), response.body());
        };
    }
}

