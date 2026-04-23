package com.tschanz.aigeny.tools;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool that queues an update to a Jira issue's summary or description.
 * The actual HTTP call is deferred until the user confirms via the UI.
 */
@Service
public class UpdateJiraIssueTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(UpdateJiraIssueTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AigenyProperties props;

    public UpdateJiraIssueTool(AigenyProperties props) {
        this.props = props;
    }

    @Override public String getName() { return "update_jira_issue"; }

    @Override
    public String getDescription() {
        return "Update fields of an existing Jira issue (summary and/or description). " +
               "The action will be queued and the user must confirm it before execution. " +
               "Provide 'issueKey' and at least one of 'summary' or 'description'.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "issueKey",    Map.of("type", "string",  "description", "The Jira issue key, e.g. NOVA-100000"),
            "summary",     Map.of("type", "string",  "description", "New summary/title for the issue"),
            "description", Map.of("type", "string",  "description", "New description text for the issue")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object",
                       "properties", propsMap,
                       "required", new String[]{"issueKey"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl() == null ? "" : jira.getBaseUrl().replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            return new ToolResult("Jira ist nicht konfiguriert.");
        }

        JsonNode args   = JSON.readTree(argumentsJson);
        String issueKey = args.path("issueKey").asText("").trim();
        String summary  = args.path("summary").asText("").trim();
        String description = args.path("description").asText("").trim();

        if (issueKey.isBlank()) {
            return new ToolResult("Bitte gib einen Jira-Issue-Key an, Towarischtsch.");
        }
        if (summary.isBlank() && description.isBlank()) {
            return new ToolResult("Bitte gib 'summary' oder 'description' an, damit ich etwas aktualisieren kann.");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (!summary.isBlank())     params.put("summary",     summary);
        if (!description.isBlank()) params.put("description", description);

        StringBuilder desc = new StringBuilder("Update Jira-Ticket **").append(issueKey).append("**:");
        if (!summary.isBlank())     desc.append("\n- **Summary** → ").append(summary);
        if (!description.isBlank()) desc.append("\n- **Description** → ").append(description);

        PendingJiraActionContext.set(new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE, issueKey, params, desc.toString()));

        log.info("Queued update_jira_issue for {} confirmation", issueKey);
        return new ToolResult(
                "Da Aktion ist bereit, Towarischtsch! " +
                "Nutzer muss Aktualisierung von **" + issueKey + "** zuerst bestätigen. " +
                "Ich habe Bestätigungs-Button in Chat gezeigt. Bitte informiere Nutzer, dass er Aktion bestätigen soll.");
    }
}

