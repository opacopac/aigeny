package com.tschanz.aigeny.orchestration;

/**
 * Carries information about a single write tool call that requires user confirmation.
 * Used to collect all pending write actions from one LLM response before showing
 * a single batch confirmation dialog.
 *
 * @param toolCallId      the LLM-assigned tool call ID (used to key pre-approved decisions)
 * @param toolName        the tool name, e.g. "create_jira_issue"
 * @param callDescription human-readable description of what this call will do
 */
public record WriteToolCallInfo(String toolCallId, String toolName, String callDescription) {}
