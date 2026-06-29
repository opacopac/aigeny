package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.AbstractTool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import com.tschanz.aigeny.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that clones an existing Jira issue.
 * It reads the source issue (and optionally its subtasks) during execute(),
 * then queues a CREATE_ISSUE action for user confirmation.
 */
@Service
public class CloneJiraIssueTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(CloneJiraIssueTool.class);
    private static final int MAX_SUBTASKS = 20;

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED   = "jira.error.not_configured_de";
    private static final String MSG_NO_TOKEN         = "jira.error.no_token";
    private static final String MSG_MISSING_KEY      = "jira.clone.missing_key";
    private static final String MSG_SOURCE_NOT_FOUND = "jira.error.issue_not_found";
    private static final String MSG_AUTH_FAILED      = "jira.error.auth_failed_en";
    private static final String MSG_HTTP_ERROR       = "jira.error.http_en";
    private static final String MSG_QUEUED           = "jira.clone.queued";
    private static final String MSG_QUEUED_SUBTASKS  = "jira.clone.queued_with_subtasks";
    private static final String MSG_WRITE_DISABLED   = "jira.write.mode_disabled";
    private static final String MSG_NO_STREAMING     = "jira.error.no_streaming_context";

    private final JiraConfiguration jiraConfig;
    private final JiraHttpClient jiraHttpClient;

    public CloneJiraIssueTool(JiraConfiguration jiraConfig, ObjectMapper objectMapper, JiraHttpClient jiraHttpClient) {
        super(objectMapper);
        this.jiraConfig = jiraConfig;
        this.jiraHttpClient = jiraHttpClient;
    }

    @Override public String getName() { return "clone_jira_issue"; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String source = args.path("sourceIssueKey").asText("?");
            String target = args.path("targetProject").asText("").trim();
            boolean subtasks = args.path("cloneSubtasks").asBoolean(false);
            String base = "Jira-Ticket klonen: " + source + (target.isBlank() ? "" : " → " + target);
            return subtasks ? base + " (inkl. Sub-Tasks)" : base;
        } catch (Exception ignored) {}
        return "Jira-Ticket klonen";
    }

    @Override
    public String getDescription() {
        return "Clone an existing Jira issue. Reads the source issue and queues the creation of a copy for user confirmation. " +
               "IMPORTANT: Only works when Jira write mode is enabled in the UI sidebar (toggle 'Jira Schreiben'). " +
               "If write mode is disabled, the tool returns an error - do NOT ask for confirmation in that case. " +
               "Required: 'sourceIssueKey' (e.g. NOVA-12345). " +
               "Optional: 'targetProject' to clone into a different project (default: same project as source), " +
               "'summaryOverride' to change the summary (default: 'Clone of <original summary>'), " +
               "'assignee' to override the assignee (technical username), " +
               "'cloneSubtasks' (boolean, default false) to also clone all sub-tasks of the issue.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = new LinkedHashMap<>();
        propsMap.put("sourceIssueKey",  Map.of("type", "string",  "description", "The Jira issue key to clone, e.g. NOVA-12345"));
        propsMap.put("targetProject",   Map.of("type", "string",  "description", "Target project key (default: same as source issue)"));
        propsMap.put("summaryOverride", Map.of("type", "string",  "description", "Override the summary of the clone (default: 'Clone of <original summary>')"));
        propsMap.put("assignee",        Map.of("type", "string",  "description", "Technical Jira username to assign the clone to (default: same as source)"));
        propsMap.put("cloneSubtasks",   Map.of("type", "boolean", "description", "Also clone all sub-tasks of the issue (default: false)"));
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap, "required", new String[]{"sourceIssueKey"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!JiraWriteContext.isEnabled()) {
            return new ToolResult(Messages.get(MSG_WRITE_DISABLED));
        }

                String baseUrl = jiraConfig.getBaseUrl() == null ? "" : jiraConfig.getBaseUrl().replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        String token = JiraTokenContext.get();
        if (token == null || token.isBlank()) token = jiraConfig.getToken();
        if (token == null || token.isBlank()) {
            return new ToolResult(Messages.get(MSG_NO_TOKEN));
        }
        String auth = "Bearer " + token;

        JsonNode args           = objectMapper.readTree(argumentsJson);
        String sourceKey        = args.path("sourceIssueKey").asText("").trim();
        String targetProject    = args.path("targetProject").asText("").trim();
        String summaryOverride  = args.path("summaryOverride").asText("").trim();
        String assigneeOverride = args.path("assignee").asText("").trim();
        boolean cloneSubtasks   = args.path("cloneSubtasks").asBoolean(false);

        if (sourceKey.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_KEY));
        }

        // ── Fetch source issue ──────────────────────────────────────────────
        String fetchUrl = baseUrl + "/rest/api/2/issue/"
                + URLEncoder.encode(sourceKey, StandardCharsets.UTF_8)
                + "?fields=summary,description,issuetype,priority,assignee,project,subtasks";
        log.info(">> JIRA GET (clone source) {}", fetchUrl);

        JsonNode source = getJson(fetchUrl, auth);
        if (source == null) return new ToolResult(Messages.get(MSG_HTTP_ERROR, "?", "Request failed"));

        // handle error nodes set via sentinel
        if (source.has("_error")) {
            int status = source.path("_status").asInt(0);
            if (status == 404) return new ToolResult(Messages.get(MSG_SOURCE_NOT_FOUND, sourceKey));
            if (status == 401) return new ToolResult(Messages.get(MSG_AUTH_FAILED));
            return new ToolResult(Messages.get(MSG_HTTP_ERROR, status, source.path("_body").asText("")));
        }

        JsonNode fields = source.path("fields");

        // ── Derive clone fields ─────────────────────────────────────────────
        String srcProjectKey = fields.path("project").path("key").asText("");
        String projectKey    = targetProject.isBlank() ? srcProjectKey : targetProject;
        String srcSummary    = fields.path("summary").asText("");
        String summary       = summaryOverride.isBlank() ? "Clone of " + srcSummary : summaryOverride;
        String issuetype     = fields.path("issuetype").path("name").asText("Task");
        String description   = fields.path("description").asText("").trim();
        String srcAssignee   = fields.path("assignee").path("name").asText("");
        String assignee      = assigneeOverride.isBlank() ? srcAssignee : assigneeOverride;

        // ── Optionally fetch sub-tasks ──────────────────────────────────────
        List<Map<String, String>> subtaskList = new ArrayList<>();
        JsonNode subtasksNode = fields.path("subtasks");
        if (cloneSubtasks && subtasksNode.isArray() && !subtasksNode.isEmpty()) {
            int count = Math.min(subtasksNode.size(), MAX_SUBTASKS);
            log.info("   Fetching {} sub-task(s) for clone", count);
            for (int i = 0; i < count; i++) {
                String stKey = subtasksNode.get(i).path("key").asText();
                String stFetchUrl = baseUrl + "/rest/api/2/issue/"
                        + URLEncoder.encode(stKey, StandardCharsets.UTF_8)
                        + "?fields=summary,description,issuetype,assignee";
                JsonNode stNode = getJson(stFetchUrl, auth);
                if (stNode == null || stNode.has("_error")) {
                    log.warn("   Could not fetch subtask {}, skipping", stKey);
                    continue;
                }
                JsonNode stFields = stNode.path("fields");
                Map<String, String> st = new LinkedHashMap<>();
                st.put("summary",     stFields.path("summary").asText(""));
                st.put("issuetype",   stFields.path("issuetype").path("name").asText("Sub-task"));
                st.put("description", stFields.path("description").asText("").trim());
                st.put("assignee",    assigneeOverride.isBlank()
                        ? stFields.path("assignee").path("name").asText("")
                        : assigneeOverride);
                subtaskList.add(st);
            }
        }

        // ── Build params & queue ────────────────────────────────────────────
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey",  projectKey);
        params.put("summary",     summary);
        params.put("issuetype",   issuetype);
        if (!description.isBlank()) params.put("description", description);
        if (!assignee.isBlank())    params.put("assignee", assignee);
        params.put("clonedFrom",  sourceKey);
        if (!subtaskList.isEmpty()) params.put("subtasks", subtaskList);

        // ── Human description ───────────────────────────────────────────────
        StringBuilder humanDesc = new StringBuilder("Jira-Ticket **" + sourceKey + "** klonen");
        if (!targetProject.isBlank() && !targetProject.equals(srcProjectKey)) {
            humanDesc.append(" → Projekt **").append(projectKey).append("**");
        }
        humanDesc.append(":\n");
        humanDesc.append("- **Typ**: ").append(issuetype).append("\n");
        humanDesc.append("- **Summary**: ").append(summary).append("\n");
        if (!assignee.isBlank()) humanDesc.append("- **Assignee**: ").append(assignee).append("\n");
        if (!subtaskList.isEmpty()) {
            humanDesc.append("- **Sub-Tasks**: ").append(subtaskList.size()).append(" werden ebenfalls geklont\n");
        }

        if (!ConfirmationContext.isAvailable()) {
            return new ToolResult(Messages.get(MSG_NO_STREAMING));
        }

        log.info("Requesting confirmation for clone_jira_issue {} → project={} subtasks={}",
                sourceKey, projectKey, subtaskList.size());
        return ConfirmationContext.get().requestConfirmation(
                humanDesc.toString(),
                new PendingJiraAction(PendingJiraAction.ActionType.CREATE_ISSUE, null, params, humanDesc.toString()));
    }

    /** Fetches a URL and returns the parsed JSON, or a sentinel node with _error/_status/_body on failure. */
    private JsonNode getJson(String url, String auth) {
        try {
            HttpResponse<String> resp = jiraHttpClient.get(url, auth);
            if (resp.statusCode() == 200) {
                return objectMapper.readTree(resp.body());
            }
            // Return sentinel error node
            return objectMapper.createObjectNode()
                    .put("_error", true)
                    .put("_status", resp.statusCode())
                    .put("_body", resp.body());
        } catch (Exception e) {
            log.error("Error fetching {}: {}", url, e.getMessage());
            return null;
        }
    }
}
