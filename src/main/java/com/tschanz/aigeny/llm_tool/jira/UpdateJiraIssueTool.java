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
 * Tool that queues an update to a Jira issue's summary or description.
 * The actual HTTP call is deferred until the user confirms via the UI.
 */
@Service
public class UpdateJiraIssueTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(UpdateJiraIssueTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    // ── Tool identity ────────────────────────────────────────────────────────
    private static final String TOOL_NAME       = "update_jira_issue";

    // ── JSON argument / parameter keys ──────────────────────────────────────
    private static final String ARG_ISSUE_KEY   = "issueKey";
    private static final String ARG_SUMMARY     = "summary";
    private static final String ARG_DESCRIPTION = "description";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED  = "jira.error.not_configured_de";
    private static final String MSG_MISSING_KEY     = "jira.update.missing_key";
    private static final String MSG_MISSING_FIELDS  = "jira.update.missing_fields";
    private static final String MSG_QUEUED          = "jira.update.queued";
    private static final String MSG_DESC_HEADER     = "jira.update.desc_header";
    private static final String MSG_DESC_SUMMARY    = "jira.update.desc_summary";
    private static final String MSG_DESC_DESC       = "jira.update.desc_description";
    private static final String MSG_TOOL_DESC       = "jira.update.tool_description";
    private static final String MSG_ARG_ISSUE_KEY   = "jira.update.arg_issue_key_desc";
    private static final String MSG_ARG_SUMMARY     = "jira.update.arg_summary_desc";
    private static final String MSG_ARG_DESCRIPTION = "jira.update.arg_description_desc";
    private static final String MSG_WRITE_DISABLED  = "jira.write.mode_disabled";

    private final AigenyProperties props;

    public UpdateJiraIssueTool(AigenyProperties props) {
        this.props = props;
    }

    @Override public String getName() { return TOOL_NAME; }

    @Override
    public String getDescription() {
        return Messages.get(MSG_TOOL_DESC);
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            ARG_ISSUE_KEY,   Map.of("type", "string", "description", Messages.get(MSG_ARG_ISSUE_KEY)),
            ARG_SUMMARY,     Map.of("type", "string", "description", Messages.get(MSG_ARG_SUMMARY)),
            ARG_DESCRIPTION, Map.of("type", "string", "description", Messages.get(MSG_ARG_DESCRIPTION))
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object",
                       "properties", propsMap,
                       "required", new String[]{ARG_ISSUE_KEY}));
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

        JsonNode args      = JSON.readTree(argumentsJson);
        String issueKey    = args.path(ARG_ISSUE_KEY).asText("").trim();
        String summary     = args.path(ARG_SUMMARY).asText("").trim();
        String description = args.path(ARG_DESCRIPTION).asText("").trim();

        if (issueKey.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_KEY));
        }
        if (summary.isBlank() && description.isBlank()) {
            return new ToolResult(Messages.get(MSG_MISSING_FIELDS));
        }

        Map<String, Object> params = new LinkedHashMap<>();
        if (!summary.isBlank())     params.put(ARG_SUMMARY,     summary);
        if (!description.isBlank()) params.put(ARG_DESCRIPTION, description);

        StringBuilder desc = new StringBuilder(Messages.get(MSG_DESC_HEADER, issueKey));
        if (!summary.isBlank())     desc.append(Messages.get(MSG_DESC_SUMMARY,  summary));
        if (!description.isBlank()) desc.append(Messages.get(MSG_DESC_DESC, description));

        PendingJiraActionContext.set(new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE, issueKey, params, desc.toString()));

        log.info("Queued update_jira_issue for {} confirmation", issueKey);
        return new ToolResult(Messages.get(MSG_QUEUED, issueKey));
    }
}

