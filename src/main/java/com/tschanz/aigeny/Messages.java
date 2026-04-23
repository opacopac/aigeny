package com.tschanz.aigeny;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Central access point for all user-facing message strings.
 * Keys map to entries in {@code messages.properties}.
 */
public final class Messages {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("messages");

    private Messages() {}

    // ── ChatController ───────────────────────────────────────────────────────
    public static final String CHAT_ERROR_EMPTY_MESSAGE   = "chat.error.empty_message";
    public static final String CHAT_ERROR_GENERIC         = "chat.error.generic";
    public static final String CHAT_JIRA_NO_PENDING       = "chat.jira.no_pending_action";
    public static final String CHAT_JIRA_NO_TOKEN         = "chat.jira.no_token";
    public static final String CHAT_JIRA_WRITE_ERROR      = "chat.jira.write_error";
    public static final String CHAT_STATUS_CANCELLED      = "chat.status.cancelled";
    public static final String CHAT_STATUS_CLEARED        = "chat.status.cleared";

    // ── OracleDbTool ─────────────────────────────────────────────────────────
    public static final String DB_ERROR_NOT_CONFIGURED    = "db.error.not_configured";
    public static final String DB_ERROR_SELECT_ONLY       = "db.error.select_only";
    public static final String DB_ERROR_DANGEROUS_SQL     = "db.error.dangerous_sql";
    public static final String DB_ERROR_NO_CONNECTION     = "db.error.no_connection";
    public static final String DB_ERROR_SQL               = "db.error.sql";

    // ── JiraTool ─────────────────────────────────────────────────────────────
    public static final String JIRA_ERROR_NOT_CONFIGURED    = "jira.error.not_configured";
    public static final String JIRA_ERROR_NOT_CONFIGURED_DE = "jira.error.not_configured_de";
    public static final String JIRA_ERROR_NO_TOKEN          = "jira.error.no_token";
    public static final String JIRA_ERROR_MISSING_JQL       = "jira.error.missing_jql_or_key";
    public static final String JIRA_ERROR_ISSUE_NOT_FOUND   = "jira.error.issue_not_found";
    public static final String JIRA_ERROR_AUTH_FAILED_EN    = "jira.error.auth_failed_en";
    public static final String JIRA_ERROR_HTTP_EN           = "jira.error.http_en";
    public static final String JIRA_NO_ISSUES_FOUND         = "jira.no_issues_found";
    public static final String JIRA_ISSUES_FOUND            = "jira.issues_found";

    // ── AddJiraCommentTool ───────────────────────────────────────────────────
    public static final String JIRA_COMMENT_MISSING_ARGS  = "jira.comment.missing_args";
    public static final String JIRA_COMMENT_QUEUED        = "jira.comment.queued";

    // ── UpdateJiraIssueTool ──────────────────────────────────────────────────
    public static final String JIRA_UPDATE_TOOL_DESCRIPTION  = "jira.update.tool_description";
    public static final String JIRA_UPDATE_ARG_ISSUE_KEY     = "jira.update.arg_issue_key_desc";
    public static final String JIRA_UPDATE_ARG_SUMMARY       = "jira.update.arg_summary_desc";
    public static final String JIRA_UPDATE_ARG_DESCRIPTION   = "jira.update.arg_description_desc";
    public static final String JIRA_UPDATE_MISSING_KEY       = "jira.update.missing_key";
    public static final String JIRA_UPDATE_MISSING_FIELDS = "jira.update.missing_fields";
    public static final String JIRA_UPDATE_QUEUED         = "jira.update.queued";
    public static final String JIRA_UPDATE_DESC_HEADER    = "jira.update.desc_header";
    public static final String JIRA_UPDATE_DESC_SUMMARY   = "jira.update.desc_summary";
    public static final String JIRA_UPDATE_DESC_DESC      = "jira.update.desc_description";

    // ── JiraWriteExecutor ────────────────────────────────────────────────────
    public static final String JIRA_WRITE_UPDATED         = "jira.write.updated";
    public static final String JIRA_WRITE_AUTH_FAILED     = "jira.write.auth_failed";
    public static final String JIRA_WRITE_FORBIDDEN       = "jira.write.forbidden";
    public static final String JIRA_WRITE_ERROR           = "jira.write.error";
    public static final String JIRA_WRITE_COMMENT_ADDED   = "jira.write.comment_added";

    // ── OrchestrationService ─────────────────────────────────────────────────
    public static final String ORCHESTRATION_PERSONA_PRIMER     = "orchestration.persona_primer";
    public static final String ORCHESTRATION_ERROR_UNKNOWN_TOOL = "orchestration.error.unknown_tool";
    public static final String ORCHESTRATION_ERROR_TOOL_EXEC    = "orchestration.error.tool_execution";
    public static final String ORCHESTRATION_ERROR_TOOL_LOOP    = "orchestration.error.tool_loop";

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Returns the message for the given key without any argument substitution.
     */
    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * Returns the message for the given key with {@link MessageFormat} argument substitution.
     */
    public static String get(String key, Object... args) {
        return MessageFormat.format(BUNDLE.getString(key), args);
    }
}

