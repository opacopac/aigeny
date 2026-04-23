package com.tschanz.aigeny.tools;

/**
 * Thread-local holder for the per-request Jira token override.
 * Set by ChatController before dispatching async work, read by JiraTool.
 */
public final class JiraTokenContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private JiraTokenContext() {}

    public static void set(String token) { TOKEN.set(token); }
    public static String get()           { return TOKEN.get(); }
    public static void clear()           { TOKEN.remove(); }
}

