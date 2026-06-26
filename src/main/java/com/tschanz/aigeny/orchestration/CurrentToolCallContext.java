package com.tschanz.aigeny.orchestration;

/**
 * ThreadLocal holder for the ID of the tool call currently being executed.
 * Set by {@link ToolExecutor} before each tool invocation so that the
 * confirmation handler in ChatStreamingService can look up pre-approved
 * decisions by tool call ID.
 */
public final class CurrentToolCallContext {

    private static final ThreadLocal<String> CURRENT_ID = new ThreadLocal<>();

    private CurrentToolCallContext() {}

    public static void set(String toolCallId) { CURRENT_ID.set(toolCallId); }
    public static String get()                { return CURRENT_ID.get(); }
    public static void clear()               { CURRENT_ID.remove(); }
}
