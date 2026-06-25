package com.tschanz.aigeny.llm_tool.jira.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import com.tschanz.aigeny.llm_tool.jira.service.IssueLinkService;
import com.tschanz.aigeny.llm_tool.jira.service.SubtaskCreationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jira operation for creating new issues.
 * Supports creating subtasks and clone links.
 */
@Component
public class CreateIssueOperation implements JiraOperation {

    private static final Logger log = LoggerFactory.getLogger(CreateIssueOperation.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String MSG_CREATED     = "jira.write.created";
    private static final String MSG_CREATED_ST  = "jira.write.created_with_subtasks";
    private static final String MSG_AUTH_FAILED = "jira.write.auth_failed";
    private static final String MSG_FORBIDDEN   = "jira.write.forbidden";
    private static final String MSG_ERROR       = "jira.write.error";

    private final JiraHttpClient httpClient;
    private final SubtaskCreationService subtaskService;
    private final IssueLinkService linkService;

    public CreateIssueOperation(JiraHttpClient httpClient,
                                SubtaskCreationService subtaskService,
                                IssueLinkService linkService) {
        this.httpClient = httpClient;
        this.subtaskService = subtaskService;
        this.linkService = linkService;
    }

    @Override
    public String execute(PendingJiraAction action, String baseUrl, String token) throws Exception {
        Map<String, Object> params = action.getParams();

        LinkedHashMap<String, Object> fields = buildFields(params);
        String body = JSON.writeValueAsString(Map.of("fields", fields));

        String url = baseUrl + "/rest/api/2/issue";
        String authHeader = "Bearer " + token;

        log.info(">> JIRA POST create issue project={} summary={}",
                params.get("projectKey"), params.get("summary"));

        HttpResponse<String> response = httpClient.post(url, body, authHeader);

        if (response.statusCode() == 201) {
            return handleSuccessfulCreation(response, params, baseUrl, token);
        } else {
            return handleCreationError(response, params);
        }
    }

    private LinkedHashMap<String, Object> buildFields(Map<String, Object> params) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", params.get("projectKey")));
        fields.put("summary", params.get("summary"));
        fields.put("issuetype", Map.of("name", params.getOrDefault("issuetype", "Task")));

        Object description = params.get("description");
        if (description != null && !description.toString().isBlank()) {
            fields.put("description", description);
        }

        Object assignee = params.get("assignee");
        if (assignee != null && !assignee.toString().isBlank()) {
            fields.put("assignee", Map.of("name", assignee));
        }

        return fields;
    }

    private String handleSuccessfulCreation(HttpResponse<String> response,
                                            Map<String, Object> params,
                                            String baseUrl, String token) throws Exception {
        String newKey = JSON.readTree(response.body()).path("key").asText("?");

        // Create "Cloners" link if this is a clone
        String clonedFrom = (String) params.get("clonedFrom");
        if (clonedFrom != null && !clonedFrom.isBlank()) {
            linkService.createCloneLink(clonedFrom, newKey, baseUrl, token);
        }

        // Clone subtasks if present
        @SuppressWarnings("unchecked")
        List<Map<String, String>> subtasks = (List<Map<String, String>>) params.get("subtasks");
        if (subtasks != null && !subtasks.isEmpty()) {
            String projectKey = (String) params.get("projectKey");
            int created = subtaskService.createSubtasks(newKey, projectKey, subtasks, baseUrl, token);
            return Messages.get(MSG_CREATED_ST, newKey, created, subtasks.size());
        }

        return Messages.get(MSG_CREATED, newKey);
    }

    private String handleCreationError(HttpResponse<String> response, Map<String, Object> params) {
        return switch (response.statusCode()) {
            case 401 -> Messages.get(MSG_AUTH_FAILED);
            case 403 -> Messages.get(MSG_FORBIDDEN, params.get("projectKey"));
            default -> Messages.get(MSG_ERROR, response.statusCode(), response.body());
        };
    }
}

