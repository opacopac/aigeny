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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes confirmed Jira write actions (update issue fields, add comments, create issues).
 */
@Service
public class JiraWriteExecutor {

    private static final Logger log = LoggerFactory.getLogger(JiraWriteExecutor.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_WRITE_DISABLED = "jira.write.mode_disabled";
    private static final String MSG_UPDATED        = "jira.write.updated";
    private static final String MSG_AUTH_FAILED    = "jira.write.auth_failed";
    private static final String MSG_FORBIDDEN      = "jira.write.forbidden";
    private static final String MSG_ERROR          = "jira.write.error";
    private static final String MSG_COMMENT_ADDED  = "jira.write.comment_added";
    private static final String MSG_CREATED        = "jira.write.created";
    private static final String MSG_CREATED_ST     = "jira.write.created_with_subtasks";

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
        if (!JiraWriteContext.isEnabled()) {
            return Messages.get(MSG_WRITE_DISABLED);
        }
        String baseUrl = props.getJira().getBaseUrl().replaceAll("/$", "");
        String auth    = "Bearer " + token;

        return switch (action.getActionType()) {
            case UPDATE_ISSUE -> updateIssue(action.getIssueKey(), action.getParams(), baseUrl, auth);
            case ADD_COMMENT  -> addComment(action.getIssueKey(), action.getParams(), baseUrl, auth);
            case CREATE_ISSUE -> createIssue(action.getParams(), baseUrl, auth);
        };
    }

    private String updateIssue(String issueKey, Map<String, Object> params,
                                String baseUrl, String auth) throws Exception {
        Map<String, Object> fields  = new LinkedHashMap<>(params);
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

        if (resp.statusCode() == 204 || resp.statusCode() == 200) return Messages.get(MSG_UPDATED, issueKey);
        if (resp.statusCode() == 401) return Messages.get(MSG_AUTH_FAILED);
        if (resp.statusCode() == 403) return Messages.get(MSG_FORBIDDEN, issueKey);
        return Messages.get(MSG_ERROR, resp.statusCode(), resp.body());
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

        if (resp.statusCode() == 201) return Messages.get(MSG_COMMENT_ADDED, issueKey);
        if (resp.statusCode() == 401) return Messages.get(MSG_AUTH_FAILED);
        if (resp.statusCode() == 403) return Messages.get(MSG_FORBIDDEN, issueKey);
        return Messages.get(MSG_ERROR, resp.statusCode(), resp.body());
    }

    private String createIssue(Map<String, Object> params, String baseUrl, String auth) throws Exception {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("project",   Map.of("key", params.get("projectKey")));
        fields.put("summary",   params.get("summary"));
        fields.put("issuetype", Map.of("name", params.getOrDefault("issuetype", "Task")));

        Object description = params.get("description");
        if (description != null && !description.toString().isBlank()) {
            fields.put("description", description);
        }
        Object assignee = params.get("assignee");
        if (assignee != null && !assignee.toString().isBlank()) {
            fields.put("assignee", Map.of("name", assignee));
        }

        String body = JSON.writeValueAsString(Map.of("fields", fields));
        String url  = baseUrl + "/rest/api/2/issue";
        log.info(">> JIRA POST create issue project={} summary={}", params.get("projectKey"), params.get("summary"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA POST create status={}", resp.statusCode());

        if (resp.statusCode() == 201) {
            String newKey = JSON.readTree(resp.body()).path("key").asText("?");

            // ── Create "Cloners" link if this is a clone ────────────────────
            String clonedFrom = (String) params.get("clonedFrom");
            if (clonedFrom != null && !clonedFrom.isBlank()) {
                createCloneLink(clonedFrom, newKey, baseUrl, auth);
            }

            // ── Clone subtasks if present ───────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Map<String, String>> subtasks = (List<Map<String, String>>) params.get("subtasks");
            if (subtasks != null && !subtasks.isEmpty()) {
                int created = 0;
                for (Map<String, String> st : subtasks) {
                    try {
                        LinkedHashMap<String, Object> stFields = new LinkedHashMap<>();
                        stFields.put("project",   Map.of("key", params.get("projectKey")));
                        stFields.put("parent",    Map.of("key", newKey));
                        stFields.put("summary",   st.get("summary"));
                        stFields.put("issuetype", Map.of("name", st.getOrDefault("issuetype", "Sub-task")));
                        String stDesc = st.get("description");
                        if (stDesc != null && !stDesc.isBlank()) stFields.put("description", stDesc);
                        String stAssignee = st.get("assignee");
                        if (stAssignee != null && !stAssignee.isBlank()) stFields.put("assignee", Map.of("name", stAssignee));

                        String stBody = JSON.writeValueAsString(Map.of("fields", stFields));
                        HttpRequest stReq = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(Duration.ofSeconds(30))
                                .header("Authorization", auth)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(stBody))
                                .build();
                        HttpResponse<String> stResp = http.send(stReq, HttpResponse.BodyHandlers.ofString());
                        if (stResp.statusCode() == 201) {
                            log.info("   Created subtask {} under {}", JSON.readTree(stResp.body()).path("key").asText(), newKey);
                            created++;
                        } else {
                            log.warn("   Failed to create subtask '{}': status={}", st.get("summary"), stResp.statusCode());
                        }
                    } catch (Exception e) {
                        log.error("   Error creating subtask '{}': {}", st.get("summary"), e.getMessage());
                    }
                }
                return Messages.get(MSG_CREATED_ST, newKey, created, subtasks.size());
            }

            return Messages.get(MSG_CREATED, newKey);
        }
        if (resp.statusCode() == 401) return Messages.get(MSG_AUTH_FAILED);
        if (resp.statusCode() == 403) return Messages.get(MSG_FORBIDDEN, params.get("projectKey"));
        return Messages.get(MSG_ERROR, resp.statusCode(), resp.body());
    }

    /**
     * Creates a "Cloners" issue link: sourceKey "is cloned by" newKey / newKey "clones" sourceKey.
     * Failure is non-fatal – logged as a warning only.
     */
    private void createCloneLink(String sourceKey, String newKey, String baseUrl, String auth) {
        try {
            // "Cloners" link type: inward = "is cloned by", outward = "clones"
            String body = JSON.writeValueAsString(Map.of(
                    "type",          Map.of("name", "Cloners"),
                    "inwardIssue",   Map.of("key", newKey),
                    "outwardIssue",  Map.of("key", sourceKey)));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rest/api/2/issueLink"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                log.info("   Created Cloners link: {} clones {}", newKey, sourceKey);
            } else {
                log.warn("   Could not create Cloners link ({} → {}): status={} body={}",
                        newKey, sourceKey, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            log.warn("   Exception creating Cloners link: {}", e.getMessage());
        }
    }
}

