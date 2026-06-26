package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.llm_tool.ToolResult;

/**
 * ThreadLocal holder for the synchronous confirmation handler.
 * Set by ChatStreamingService before running the orchestration loop.
 *
 * <p>Write tools call {@link #get()}.requestConfirmation(...) instead of queuing
 * a PendingJiraAction.  The handler sends a {@code confirmation_required} SSE event
 * to the client, blocks the orchestration thread until the user decides, then
 * executes the action (confirmed) or returns a "declined" ToolResult.
 */
public final class ConfirmationContext {

    @FunctionalInterface
    public interface Handler {
        /**
         * Requests user confirmation for a pending Jira write action.
         *
         * @param humanDescription markdown-formatted description shown to the user
         * @param action           the action to execute if the user confirms
         * @return execution result when confirmed, or a "declined" message when not
         */
        ToolResult requestConfirmation(String humanDescription, PendingJiraAction action);
    }

    private static final ThreadLocal<Handler> HANDLER = new ThreadLocal<>();

    private ConfirmationContext() {}

    public static void set(Handler handler) { HANDLER.set(handler); }
    public static Handler get()             { return HANDLER.get(); }
    public static void clear()              { HANDLER.remove(); }
    public static boolean isAvailable()     { return HANDLER.get() != null; }
}

