package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool that queues the creation of a new Jira issue.
 * The actual HTTP call is deferred until the user confirms via the UI.
 */
@Service
public class CreateJiraIssueTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CreateJiraIssueTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED = "jira.error.not_configured_de";
    private static final String MSG_MISSING_ARGS   = "jira.create.missing_args";
    private static final String MSG_QUEUED         = "jira.create.queued";
    private static final String MSG_WRITE_DISABLED = "jira.write.mode_disabled";

    private final AigenyProperties props;

    public CreateJiraIssueTool(AigenyProperties props) {
        this.props = props;
    }

    @Override public String getName() { return "create_jira_issue"; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = JSON.readTree(argumentsJson);
            String project = args.path("project").asText("?");
            String summary = args.path("summary").asText("").trim();
            String display = summary.length() > 50 ? summary.substring(0, 47) + "..." : summary;
            return "Jira-Ticket erstellen in " + project + ": " + display;
        } catch (Exception ignored) {}
        return "Jira-Ticket erstellen";
    }

    @Override
    public String getDescription() {
        return "Create a new Jira issue. " +
               "IMPORTANT: Only works when Jira write mode is enabled in the UI sidebar (toggle 'Jira Schreiben'). " +
               "If write mode is disabled, the tool returns an error - do NOT ask for confirmation in that case. " +
               "If write mode is enabled, the action will be queued and the user must confirm it before execution. " +
               "Required: 'project' (project key, e.g. NOVA) and 'summary'. " +
               "Optional: 'issuetype' (default: Task), 'description', 'assignee' (technical username, e.g. u123456).";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = new LinkedHashMap<>();
        propsMap.put("project",     Map.of("type", "string", "description", "Jira project key, e.g. NOVA"));
        propsMap.put("summary",     Map.of("type", "string", "description", "Issue summary / title"));
        propsMap.put("issuetype",   Map.of("type", "string", "description", "Issue type, e.g. Task, Bug, Story (default: Task)"));
        propsMap.put("description", Map.of("type", "string", "description", "Issue description text (plain text / Jira wiki markup, not Markdown)"));
        propsMap.put("assignee",    Map.of("type", "string", "description", "Technical Jira username to assign the issue to, e.g. u123456"));
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap, "required", new String[]{"project", "summary"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!JiraWriteContext.isEnabled()) {
            return new ToolResult(Messages.get(MSG_WRITE_DISABLED));
        }
        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl() == null ? "" : jira.getBaseUrl().replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        JsonNode args       = JSON.readTree(argumentsJson);
        String projectKey   = args.path("project").asText("").trim();
        String summary      = args.path("summary").asText("").trim();
        String issuetype    = args.path("issuetype").asText("Task").trim();
        String description  = args.path("description").asText("").trim();
        String assignee     = args.path("assignee").asText("").trim();

        if (projectKey.isBlank() || summary.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_ARGS));
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey",  projectKey);
        params.put("summary",     summary);
        params.put("issuetype",   issuetype);
        if (!description.isBlank()) params.put("description", description);
        if (!assignee.isBlank())    params.put("assignee", assignee);

        StringBuilder humanDesc = new StringBuilder(
                "Neues Jira-Ticket in Projekt **" + projectKey + "** erstellen:\n");
        humanDesc.append("- **Typ**: ").append(issuetype).append("\n");
        humanDesc.append("- **Summary**: ").append(summary).append("\n");
        if (!description.isBlank()) humanDesc.append("- **Beschreibung**: ").append(
                description.length() > 200 ? description.substring(0, 197) + "..." : description).append("\n");
        if (!assignee.isBlank()) humanDesc.append("- **Assignee**: ").append(assignee).append("\n");

        PendingJiraActionContext.add(new PendingJiraAction(
                PendingJiraAction.ActionType.CREATE_ISSUE, null, params, humanDesc.toString()));

        log.info("Queued create_jira_issue for project={} confirmation", projectKey);
        return new ToolResult(Messages.get(MSG_QUEUED, projectKey));
    }
}

