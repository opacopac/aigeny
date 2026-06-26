package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.AbstractTool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Tool that queues adding a comment to a Jira issue.
 * The actual HTTP call is deferred until the user confirms via the UI.
 */
@Service
public class AddJiraCommentTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(AddJiraCommentTool.class);

    // ── Tool identity ────────────────────────────────────────────────────────
    private static final String TOOL_NAME    = "add_jira_comment";

    // ── JSON argument keys ───────────────────────────────────────────────────
    private static final String ARG_ISSUE_KEY = "issueKey";
    private static final String ARG_COMMENT   = "comment";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED = "jira.error.not_configured_de";
    private static final String MSG_MISSING_ARGS   = "jira.comment.missing_args";
    private static final String MSG_WRITE_DISABLED = "jira.write.mode_disabled";
    private static final String MSG_NO_STREAMING   = "jira.error.no_streaming_context";

    private final JiraConfiguration jiraConfig;

    public AddJiraCommentTool(JiraConfiguration jiraConfig, ObjectMapper objectMapper) {
        super(objectMapper);
        this.jiraConfig = jiraConfig;
    }

    @Override public String getName() { return TOOL_NAME; }
    @Override public boolean requiresConfirmation() { return true; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String issueKey = args.path(ARG_ISSUE_KEY).asText("").trim();
            return "Kommentar hinzufügen zu " + (issueKey.isBlank() ? "Jira-Ticket" : issueKey);
        } catch (Exception ignored) {}
        return "Jira-Kommentar hinzufügen";
    }

    @Override
    public String getDescription() {
        return "Add a comment to an existing Jira issue. " +
               "IMPORTANT: Only works when Jira write mode is enabled in the UI sidebar (toggle 'Jira Schreiben'). " +
               "If write mode is disabled, the tool returns an error - do NOT ask for confirmation in that case. " +
               "If write mode is enabled, the action will be queued and the user must confirm it before execution. " +
               "Provide 'issueKey' and 'comment'.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            ARG_ISSUE_KEY, Map.of("type", "string", "description", "The Jira issue key, e.g. NOVA-100000"),
            ARG_COMMENT,   Map.of("type", "string", "description", "The comment text to add to the issue")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object",
                       "properties", propsMap,
                       "required", new String[]{ARG_ISSUE_KEY, ARG_COMMENT}));
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

        JsonNode args   = objectMapper.readTree(argumentsJson);
        String issueKey = args.path(ARG_ISSUE_KEY).asText("").trim();
        String comment  = args.path(ARG_COMMENT).asText("").trim();

        if (issueKey.isBlank() || comment.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_ARGS));
        }

        if (!ConfirmationContext.isAvailable()) {
            return new ToolResult(Messages.get(MSG_NO_STREAMING));
        }

        String humanDesc = "Kommentar zu Jira-Ticket **" + issueKey + "** hinzufügen:\n> " + comment;

        log.info("Requesting confirmation for add_jira_comment on {}", issueKey);
        return ConfirmationContext.get().requestConfirmation(
                humanDesc,
                new PendingJiraAction(PendingJiraAction.ActionType.ADD_COMMENT, issueKey,
                        Map.of(ARG_COMMENT, comment), humanDesc));
    }
}
