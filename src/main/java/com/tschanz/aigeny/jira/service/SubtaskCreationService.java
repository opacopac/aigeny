package com.tschanz.aigeny.jira.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.jira.JiraHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for creating subtasks in Jira.
 */
@Service
public class SubtaskCreationService {

    private static final Logger log = LoggerFactory.getLogger(SubtaskCreationService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JiraHttpClient httpClient;

    public SubtaskCreationService(JiraHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Create subtasks for a parent issue.
     *
     * @param parentKey The parent issue key
     * @param projectKey The project key
     * @param subtasks List of subtask definitions
     * @param baseUrl Jira base URL
     * @param token Bearer token
     * @return Number of successfully created subtasks
     */
    public int createSubtasks(String parentKey, String projectKey,
                              List<Map<String, String>> subtasks,
                              String baseUrl, String token) {
        if (subtasks == null || subtasks.isEmpty()) {
            return 0;
        }

        int created = 0;
        String url = baseUrl + "/rest/api/2/issue";
        String authHeader = "Bearer " + token;

        for (Map<String, String> subtask : subtasks) {
            try {
                LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
                fields.put("project", Map.of("key", projectKey));
                fields.put("parent", Map.of("key", parentKey));
                fields.put("summary", subtask.get("summary"));
                fields.put("issuetype", Map.of("name", subtask.getOrDefault("issuetype", "Sub-task")));

                String description = subtask.get("description");
                if (description != null && !description.isBlank()) {
                    fields.put("description", description);
                }

                String assignee = subtask.get("assignee");
                if (assignee != null && !assignee.isBlank()) {
                    fields.put("assignee", Map.of("name", assignee));
                }

                String body = JSON.writeValueAsString(Map.of("fields", fields));
                HttpResponse<String> response = httpClient.post(url, body, authHeader);

                if (response.statusCode() == 201) {
                    String subtaskKey = JSON.readTree(response.body()).path("key").asText();
                    log.info("   Created subtask {} under {}", subtaskKey, parentKey);
                    created++;
                } else {
                    log.warn("   Failed to create subtask '{}': status={}",
                            subtask.get("summary"), response.statusCode());
                }
            } catch (Exception e) {
                log.error("   Error creating subtask '{}': {}",
                        subtask.get("summary"), e.getMessage());
            }
        }

        return created;
    }
}

