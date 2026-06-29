package com.tschanz.aigeny.tool.jira.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.tool.jira.PendingJiraAction;
import com.tschanz.aigeny.tool.jira.http.JiraHttpClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Jira operation for adding comments to issues.
 */
@Component
public class AddCommentOperation implements JiraOperation {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MSG_COMMENT_ADDED = "jira.write.comment_added";
    private static final String MSG_AUTH_FAILED   = "jira.write.auth_failed";
    private static final String MSG_FORBIDDEN     = "jira.write.forbidden";
    private static final String MSG_ERROR         = "jira.write.error";

    private final JiraHttpClient httpClient;

    public AddCommentOperation(JiraHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String execute(PendingJiraAction action, String baseUrl, String token) throws Exception {
        String issueKey = action.getIssueKey();
        Map<String, Object> params = action.getParams();

        String commentText = (String) params.get("comment");
        String body = JSON.writeValueAsString(Map.of("body", commentText));

        String url = baseUrl + "/rest/api/2/issue/"
                + URLEncoder.encode(issueKey, StandardCharsets.UTF_8) + "/comment";
        String authHeader = "Bearer " + token;

        HttpResponse<String> response = httpClient.post(url, body, authHeader);

        return switch (response.statusCode()) {
            case 201 -> Messages.get(MSG_COMMENT_ADDED, issueKey);
            case 401 -> Messages.get(MSG_AUTH_FAILED);
            case 403 -> Messages.get(MSG_FORBIDDEN, issueKey);
            default -> Messages.get(MSG_ERROR, response.statusCode(), response.body());
        };
    }
}

