package com.tschanz.aigeny.llm_tool.jira;

/**
 * ThreadLocal holder for the Jira write mode flag.
 * Set by ChatController before executing async tasks, mirroring the pattern of JiraTokenContext.
 */
public class JiraWriteContext {

    private static final ThreadLocal<Boolean> WRITE_ENABLED = new ThreadLocal<>();

    public static void set(boolean enabled) { WRITE_ENABLED.set(enabled); }

    public static boolean isEnabled() {
        Boolean val = WRITE_ENABLED.get();
        return val != null && val;
    }

    public static void clear() { WRITE_ENABLED.remove(); }

    private JiraWriteContext() {}
}

