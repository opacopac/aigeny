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

import java.util.Map;

/**
 * Tool that queues adding a comment to a Jira issue.
 * The actual HTTP call is deferred until the user confirms via the UI.
 */
@Service
public class AddJiraCommentTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(AddJiraCommentTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Tool identity ────────────────────────────────────────────────────────
    private static final String TOOL_NAME    = "add_jira_comment";

    // ── JSON argument keys ───────────────────────────────────────────────────
    private static final String ARG_ISSUE_KEY = "issueKey";
    private static final String ARG_COMMENT   = "comment";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED = "jira.error.not_configured_de";
    private static final String MSG_MISSING_ARGS   = "jira.comment.missing_args";
    private static final String MSG_QUEUED         = "jira.comment.queued";

    private final AigenyProperties props;

    public AddJiraCommentTool(AigenyProperties props) {
        this.props = props;
    }

    @Override public String getName() { return TOOL_NAME; }

    @Override
    public String getDescription() {
        return "Add a comment to an existing Jira issue. " +
               "The action will be queued and the user must confirm it before execution. " +
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
        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl() == null ? "" : jira.getBaseUrl().replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        JsonNode args   = JSON.readTree(argumentsJson);
        String issueKey = args.path(ARG_ISSUE_KEY).asText("").trim();
        String comment  = args.path(ARG_COMMENT).asText("").trim();

        if (issueKey.isBlank() || comment.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_ARGS));
        }

        String humanDesc = "Kommentar zu Jira-Ticket **" + issueKey + "** hinzufügen:\n> " + comment;

        PendingJiraActionContext.set(new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, issueKey,
                Map.of(ARG_COMMENT, comment), humanDesc));

        log.info("Queued add_jira_comment for {} confirmation", issueKey);
        return new ToolResult(Messages.get(MSG_QUEUED, issueKey));
    }
}

