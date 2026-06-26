package com.tschanz.aigeny.orchestration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * ThreadLocal holder for the batch confirmation handler.
 * Set by {@code ChatStreamingService} before calling the orchestration loop.
 * Used by {@code OrchestrationService} when it detects multiple write tool calls
 * in a single LLM response to request one combined confirmation dialog.
 *
 * <p>The handler receives a list of {@link WriteToolCallInfo} objects and returns
 * a map of {@code toolCallId → confirmed} decisions.
 */
public final class BatchConfirmationContext {

    private static final ThreadLocal<Function<List<WriteToolCallInfo>, Map<String, Boolean>>> HANDLER
            = new ThreadLocal<>();

    private BatchConfirmationContext() {}

    public static void set(Function<List<WriteToolCallInfo>, Map<String, Boolean>> handler) {
        HANDLER.set(handler);
    }

    public static Function<List<WriteToolCallInfo>, Map<String, Boolean>> get() {
        return HANDLER.get();
    }

    public static void clear() {
        HANDLER.remove();
    }

    public static boolean isAvailable() {
        return HANDLER.get() != null;
    }
}
