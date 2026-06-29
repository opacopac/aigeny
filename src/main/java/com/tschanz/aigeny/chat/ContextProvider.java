package com.tschanz.aigeny.chat;

/**
 * Extension point for per-request execution contexts.
 *
 * <p>Each integration (Jira, Bitbucket, …) registers one {@code ContextProvider} bean.
 * {@link ExecutionContextManager} discovers all beans automatically and calls them in bulk,
 * so adding a new integration never requires modifying the manager.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>{@link #getKey()} returns the token-map key used by callers to supply this provider's
 *       token (e.g. {@code "jira"} or {@code "bitbucket"}).</li>
 *   <li>{@link #setup(String, boolean)} is called once per request thread before orchestration
 *       begins.  {@code token} may be {@code null} if the user did not supply one.</li>
 *   <li>{@link #cleanup()} is called in a {@code finally} block after orchestration completes.
 *       Implementations must remove all ThreadLocals they own to prevent memory leaks.</li>
 * </ul>
 *
 * <h2>Adding a new integration</h2>
 * <ol>
 *   <li>Create a new {@code @Service} class implementing this interface.</li>
 *   <li>In callers ({@code ChatStreamingService}, {@code ChatController}) add the token to the
 *       {@code Map<String, String>} tokens map using the new key.</li>
 *   <li>No other changes needed – Spring auto-collects the bean via the {@code List<ContextProvider>}
 *       constructor parameter of {@link ExecutionContextManager}.</li>
 * </ol>
 */
public interface ContextProvider {

    /**
     * Key under which callers supply this provider's token in the
     * {@code Map<String, String> tokens} map passed to
     * {@link ExecutionContextManager#setupContexts}.
     */
    String getKey();

    /**
     * Sets up the ThreadLocal context(s) owned by this provider for the current request thread.
     *
     * @param token        the API token for this integration; may be {@code null}
     * @param writeEnabled whether write operations are enabled for this request
     */
    void setup(String token, boolean writeEnabled);

    /**
     * Removes all ThreadLocal values owned by this provider.
     * Must be idempotent and must not throw.
     */
    void cleanup();
}

