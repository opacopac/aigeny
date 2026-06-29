package com.tschanz.aigeny.web;

import jakarta.servlet.http.HttpSession;

/**
 * Narrow session interface for Jira write-mode management (ISP, I-1).
 *
 * <p>Consumers that only need to toggle or query the Jira write-mode flag
 * depend on this interface instead of the broad {@link ChatSessionService}.
 */
public interface SessionJiraWriteService {

    /**
     * Enables or disables Jira write mode for the session.
     *
     * @param session HTTP session
     * @param enabled {@code true} to enable write operations, {@code false} to disable
     */
    void setJiraWriteMode(HttpSession session, boolean enabled);

    /**
     * Returns {@code true} if Jira write mode is currently enabled for the session.
     *
     * @param session HTTP session
     */
    boolean isJiraWriteModeEnabled(HttpSession session);
}

