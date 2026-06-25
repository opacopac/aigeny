package com.tschanz.aigeny.llm_tool.jira;

import java.util.ArrayList;
import java.util.List;

/**
 * ThreadLocal holder for pending Jira write actions that require user confirmation.
 * Supports multiple actions queued in one orchestration round.
 */
public final class PendingJiraActionContext {

    private static final ThreadLocal<List<PendingJiraAction>> CONTEXT = new ThreadLocal<>();

    private PendingJiraActionContext() {}

    public static void add(PendingJiraAction action) {
        List<PendingJiraAction> list = CONTEXT.get();
        if (list == null) {
            list = new ArrayList<>();
            CONTEXT.set(list);
        }
        list.add(action);
    }

    public static List<PendingJiraAction> get() { return CONTEXT.get(); }
    public static void clear()                  { CONTEXT.remove(); }
}
