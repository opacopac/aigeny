package com.tschanz.aigeny.tools;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
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

    private final AigenyProperties props;

    public AddJiraCommentTool(AigenyProperties props) {
        this.props = props;
    }

    @Override public String getName() { return "add_jira_comment"; }

    @Override
    public String getDescription() {
        return "Add a comment to an existing Jira issue. " +
               "The action will be queued and the user must confirm it before execution. " +
               "Provide 'issueKey' and 'comment'.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "issueKey", Map.of("type", "string", "description", "The Jira issue key, e.g. NOVA-100000"),
            "comment",  Map.of("type", "string", "description", "The comment text to add to the issue")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object",
                       "properties", propsMap,
                       "required", new String[]{"issueKey", "comment"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl() == null ? "" : jira.getBaseUrl().replaceAll("/$", "");
        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(Messages.JIRA_ERROR_NOT_CONFIGURED_DE));
        }

        JsonNode args   = JSON.readTree(argumentsJson);
        String issueKey = args.path("issueKey").asText("").trim();
        String comment  = args.path("comment").asText("").trim();

        if (issueKey.isBlank() || comment.isBlank()) {
            return new ToolResult(Messages.get(Messages.JIRA_COMMENT_MISSING_ARGS));
        }

        String humanDesc = "Kommentar zu Jira-Ticket **" + issueKey + "** hinzufügen:\n> " + comment;

        PendingJiraActionContext.set(new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, issueKey,
                Map.of("comment", comment), humanDesc));

        log.info("Queued add_jira_comment for {} confirmation", issueKey);
        return new ToolResult(Messages.get(Messages.JIRA_COMMENT_QUEUED, issueKey));
    }
}

