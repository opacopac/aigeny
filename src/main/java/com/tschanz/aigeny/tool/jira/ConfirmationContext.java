package com.tschanz.aigeny.tool.jira;

import com.tschanz.aigeny.tool.ToolResult;

import java.util.Map;

/**
 * ThreadLocal holder for the synchronous confirmation handler.
 * Set by ChatStreamingService before running the orchestration loop.
 *
 * <p>Write tools call {@link #get()}.requestConfirmation(...) instead of queuing
 * a PendingJiraAction.  The handler sends a {@code confirmation_required} SSE event
 * to the client, blocks the orchestration thread until the user decides, then
 * executes the action (confirmed) or returns a "declined" ToolResult.
 *
 * <p>When the LLM returns multiple write tool calls in one response, a batch
 * confirmation dialog collects all decisions upfront. Pre-approved decisions are
 * stored here (keyed by tool call ID) so individual write tools skip the blocking
 * confirmation flow and use the pre-stored decision directly.
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

    private static final ThreadLocal<Handler>              HANDLER      = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Boolean>> PRE_APPROVALS = new ThreadLocal<>();

    private ConfirmationContext() {}

    public static void set(Handler handler)  { HANDLER.set(handler); }
    public static Handler get()              { return HANDLER.get(); }
    public static void clear()               { HANDLER.remove(); }
    public static boolean isAvailable()      { return HANDLER.get() != null; }

    /** Store pre-approved decisions from a batch confirmation dialog (toolCallId → confirmed). */
    public static void setPreApprovedDecisions(Map<String, Boolean> decisions) {
        PRE_APPROVALS.set(decisions);
    }

    /** Returns true if a pre-approved decision exists for the given tool call ID. */
    public static boolean hasPreApproved(String toolCallId) {
        Map<String, Boolean> map = PRE_APPROVALS.get();
        return map != null && map.containsKey(toolCallId);
    }

    /** Returns the pre-approved decision for the given tool call ID, or false if absent. */
    public static boolean getPreApproved(String toolCallId) {
        Map<String, Boolean> map = PRE_APPROVALS.get();
        return map != null && Boolean.TRUE.equals(map.get(toolCallId));
    }

    /** Clears all pre-approved decisions. */
    public static void clearPreApproved() {
        PRE_APPROVALS.remove();
    }
}

