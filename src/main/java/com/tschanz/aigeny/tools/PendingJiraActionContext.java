package com.tschanz.aigeny.tools;

/**
 * ThreadLocal holder for a pending Jira write action that requires user confirmation.
 * Set by write tools during orchestration, read by ChatController to embed in response.
 */
public final class PendingJiraActionContext {

    private static final ThreadLocal<PendingJiraAction> CONTEXT = new ThreadLocal<>();

    private PendingJiraActionContext() {}

    public static void set(PendingJiraAction action) { CONTEXT.set(action); }
    public static PendingJiraAction get()            { return CONTEXT.get(); }
    public static void clear()                       { CONTEXT.remove(); }
}

