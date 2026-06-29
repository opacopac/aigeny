package com.tschanz.aigeny.jira.operation;

import com.tschanz.aigeny.jira.confirmation.PendingJiraAction;

/**
 * Strategy interface for executing different types of Jira operations.
 * Each operation type (UPDATE, CREATE, COMMENT) has its own implementation.
 */
public interface JiraOperation {

    /**
     * Execute the Jira operation.
     *
     * @param action The pending action containing operation details
     * @param baseUrl Jira base URL (without trailing slash)
     * @param token Bearer token for authentication
     * @return Human-readable result message
     * @throws Exception if the operation fails
     */
    String execute(PendingJiraAction action, String baseUrl, String token) throws Exception;
}

