package com.tschanz.aigeny.tool.jira;

import com.tschanz.aigeny.tool.ToolResult;

/**
 * Service interface for requesting user confirmation before executing a Jira write action.
 *
 * <p>Write tools depend on this interface instead of the static {@link ConfirmationContext},
 * following the Dependency Inversion Principle (D-1). This makes write tools fully testable
 * without a running streaming context.
 *
 * <p>The default implementation {@link ThreadLocalConfirmationService} delegates to the
 * ThreadLocal-backed {@link ConfirmationContext}.  Tests may inject a mock or stub.
 */
public interface ConfirmationService {

    /**
     * Returns {@code true} if a confirmation handler is currently active, i.e. the tool is
     * being executed inside a streaming (SSE) request context.
     *
     * <p>Write tools must check this before calling {@link #requestConfirmation} and return an
     * error message when the context is not available (non-streaming path).
     */
    boolean isAvailable();

    /**
     * Requests user confirmation for a pending Jira write action.
     *
     * <p>Sends a {@code confirmation_required} SSE event to the client, blocks the current
     * thread until the user responds, and then executes the action (confirmed) or returns a
     * "declined" {@link ToolResult}.
     *
     * @param humanDescription markdown-formatted description shown to the user
     * @param action           the action to execute if the user confirms
     * @return execution result when confirmed, or a "declined" / timeout message when not
     */
    ToolResult requestConfirmation(String humanDescription, PendingJiraAction action);
}

