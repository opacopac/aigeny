package com.tschanz.aigeny.jira.confirmation;

import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.jira.operation.AddCommentOperation;
import com.tschanz.aigeny.jira.operation.CreateIssueOperation;
import com.tschanz.aigeny.jira.operation.JiraOperation;
import com.tschanz.aigeny.jira.operation.UpdateIssueOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * Executes confirmed Jira write actions (update issue fields, add comments, create issues).
 * Uses Strategy Pattern to delegate to specific operation implementations.
 */
@Service
public class JiraWriteExecutor {

    private static final Logger log = LoggerFactory.getLogger(JiraWriteExecutor.class);
    private static final String MSG_WRITE_DISABLED = "jira.write.mode_disabled";

    private final JiraConfiguration jiraConfig;
    private final Map<PendingJiraAction.ActionType, JiraOperation> operations;

    public JiraWriteExecutor(JiraConfiguration jiraConfig,
                             UpdateIssueOperation updateOperation,
                             AddCommentOperation commentOperation,
                             CreateIssueOperation createOperation) {
        this.jiraConfig = jiraConfig;
        this.operations = new EnumMap<>(PendingJiraAction.ActionType.class);
        this.operations.put(PendingJiraAction.ActionType.UPDATE_ISSUE, updateOperation);
        this.operations.put(PendingJiraAction.ActionType.ADD_COMMENT, commentOperation);
        this.operations.put(PendingJiraAction.ActionType.CREATE_ISSUE, createOperation);
    }

    /**
     * Execute the given pending action using the provided Jira token.
     * @return human-readable result message
     */
    public String execute(PendingJiraAction action, String token) throws Exception {
        if (!JiraWriteContext.isEnabled()) {
            return Messages.get(MSG_WRITE_DISABLED);
        }

        String baseUrl = jiraConfig.getBaseUrl().replaceAll("/$", "");
        JiraOperation operation = operations.get(action.getActionType());

        if (operation == null) {
            throw new IllegalArgumentException("Unknown action type: " + action.getActionType());
        }

        log.debug("Executing Jira operation: {} using {}",
                action.getActionType(), operation.getClass().getSimpleName());

        return operation.execute(action, baseUrl, token);
    }
}
