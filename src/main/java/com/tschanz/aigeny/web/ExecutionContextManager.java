package com.tschanz.aigeny.web;

import com.tschanz.aigeny.tool.jira.ConfirmationContext;
import com.tschanz.aigeny.orchestration.BatchConfirmationContext;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Manages per-request ThreadLocal execution contexts for chat processing.
 *
 * <p>Token-based contexts (Jira, Bitbucket, …) are delegated to the registered
 * {@link ContextProvider} beans, so this class never needs to change when a new
 * integration is added.  Only the confirmation-related ThreadLocals
 * ({@link ConfirmationContext}, {@link BatchConfirmationContext}) are managed directly here
 * because they carry behaviour (handlers / callbacks) rather than simple token values.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Call {@link #setupContexts} once before orchestration begins.</li>
 *   <li>Call {@link #cleanupAllContexts} in a {@code finally} block to prevent memory leaks.</li>
 * </ol>
 */
@Service
public class ExecutionContextManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionContextManager.class);

    private final List<ContextProvider> contextProviders;

    /**
     * Spring auto-collects all {@link ContextProvider} beans into the list.
     */
    public ExecutionContextManager(List<ContextProvider> contextProviders) {
        this.contextProviders = contextProviders;
        log.info("ExecutionContextManager initialized with {} context provider(s): {}",
                contextProviders.size(),
                contextProviders.stream().map(ContextProvider::getKey).toList());
    }

    /**
     * Sets up all per-request ThreadLocal contexts.
     *
     * <p>Each registered {@link ContextProvider} receives the token stored under its
     * {@link ContextProvider#getKey() key} from the {@code tokens} map (or {@code null}
     * if the key is absent).  The {@code writeEnabled} flag is forwarded to every provider;
     * providers that do not use it simply ignore it.
     *
     * <p>Confirmation-related contexts are set up directly here because they carry
     * callbacks rather than plain token values.
     *
     * @param tokens                   map of integration key → API token (null values allowed)
     * @param writeEnabled             whether write operations are enabled for this request
     * @param confirmationHandler      handler for single write-tool confirmations (may be {@code null})
     * @param batchConfirmationHandler handler for batch write-tool confirmations (may be {@code null})
     */
    public void setupContexts(Map<String, String> tokens,
                              boolean writeEnabled,
                              ConfirmationContext.Handler confirmationHandler,
                              Function<List<WriteToolCallInfo>, Map<String, Boolean>> batchConfirmationHandler) {
        log.debug("Setting up execution contexts (writeEnabled={}, providers={})",
                writeEnabled, contextProviders.stream().map(ContextProvider::getKey).toList());

        for (ContextProvider provider : contextProviders) {
            provider.setup(tokens.get(provider.getKey()), writeEnabled);
        }

        ConfirmationContext.set(confirmationHandler);
        BatchConfirmationContext.set(batchConfirmationHandler);
    }

    /**
     * Clears all per-request ThreadLocal contexts to prevent memory leaks.
     *
     * <p>Must be called in a {@code finally} block after every
     * {@link #setupContexts} invocation.
     */
    public void cleanupAllContexts() {
        log.debug("Cleaning up all execution contexts");

        for (ContextProvider provider : contextProviders) {
            provider.cleanup();
        }

        ConfirmationContext.clear();
        ConfirmationContext.clearPreApproved();
        BatchConfirmationContext.clear();
    }
}
