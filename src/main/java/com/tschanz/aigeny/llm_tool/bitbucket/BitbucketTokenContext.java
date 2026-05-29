package com.tschanz.aigeny.llm_tool.bitbucket;

/**
 * Thread-local holder for the per-request Bitbucket token override.
 * Set by ChatController before dispatching async work, read by Bitbucket tools.
 */
public final class BitbucketTokenContext {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    private BitbucketTokenContext() {}

    public static void set(String token) { TOKEN.set(token); }
    public static String get()           { return TOKEN.get(); }
    public static void clear()           { TOKEN.remove(); }
}

