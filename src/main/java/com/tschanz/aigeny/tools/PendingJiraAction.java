package com.tschanz.aigeny.tools;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a Jira write action that is waiting for user confirmation.
 */
public class PendingJiraAction implements Serializable {

    public enum ActionType { UPDATE_ISSUE, ADD_COMMENT }

    private final ActionType actionType;
    private final String issueKey;
    private final Map<String, Object> params;
    private final String humanDescription;

    public PendingJiraAction(ActionType actionType, String issueKey,
                             Map<String, Object> params, String humanDescription) {
        this.actionType = actionType;
        this.issueKey = issueKey;
        this.params = params;
        this.humanDescription = humanDescription;
    }

    public ActionType getActionType()      { return actionType; }
    public String getIssueKey()            { return issueKey; }
    public Map<String, Object> getParams() { return params; }
    public String getHumanDescription()    { return humanDescription; }
}

