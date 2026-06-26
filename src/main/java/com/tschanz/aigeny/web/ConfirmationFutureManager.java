package com.tschanz.aigeny.web;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages CompletableFuture instances for user confirmations during chat operations.
 * 
 * <p>When a write tool requires user confirmation, the orchestration thread:
 * <ol>
 *   <li>Creates a CompletableFuture via this service</li>
 *   <li>Sends a confirmation request SSE event to the client</li>
 *   <li>Blocks waiting for the future to complete</li>
 *   <li>Resumes when the user calls the confirmation endpoint</li>
 * </ol>
 * 
 * <p>This service handles both single confirmations (one action at a time) and
 * batch confirmations (multiple actions presented together).
 * 
 * <p>Futures are stored in the HTTP session and cleaned up after resolution.
 */
@Service
public class ConfirmationFutureManager {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationFutureManager.class);

    // Session attribute keys
    private static final String SESSION_CONFIRMATION_FUTURE  = "jiraConfirmationFuture";
    private static final String SESSION_BATCH_CONFIRM_FUTURE = "jiraBatchConfirmFuture";

    // ── Single Confirmation Management ───────────────────────────────────────

    /**
     * Creates a new CompletableFuture for a single action confirmation.
     * Stored in the session so the confirmation endpoint can resolve it.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user decides
     */
    public CompletableFuture<Boolean> createSingleConfirmationFuture(HttpSession session) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        session.setAttribute(SESSION_CONFIRMATION_FUTURE, future);
        log.debug("Created single confirmation future for session {}", session.getId());
        return future;
    }

    /**
     * Resolves the pending single confirmation future with the user's decision.
     * Removes it from the session after resolving.
     *
     * @param session   HTTP session
     * @param confirmed true if the user confirmed, false if declined
     * @return true if a pending future was found and resolved, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean resolveSingleConfirmation(HttpSession session, boolean confirmed) {
        CompletableFuture<Boolean> future =
                (CompletableFuture<Boolean>) session.getAttribute(SESSION_CONFIRMATION_FUTURE);
        if (future != null) {
            session.removeAttribute(SESSION_CONFIRMATION_FUTURE);
            future.complete(confirmed);
            log.debug("Single confirmation resolved ({}) for session {}", 
                     confirmed ? "confirmed" : "declined", session.getId());
            return true;
        }
        log.warn("resolveSingleConfirmation called but no pending future for session {}", session.getId());
        return false;
    }

    // ── Batch Confirmation Management ────────────────────────────────────────

    /**
     * Creates a new CompletableFuture for a batch confirmation dialog.
     * The future resolves with a map of {@code toolCallId → confirmed}.
     * Stored in the session so the batch confirmation endpoint can resolve it.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user submits all decisions
     */
    public CompletableFuture<Map<String, Boolean>> createBatchConfirmationFuture(HttpSession session) {
        CompletableFuture<Map<String, Boolean>> future = new CompletableFuture<>();
        session.setAttribute(SESSION_BATCH_CONFIRM_FUTURE, future);
        log.debug("Created batch confirmation future for session {}", session.getId());
        return future;
    }

    /**
     * Resolves the pending batch confirmation future with the user's decisions.
     * Removes it from the session after resolving.
     *
     * @param session   HTTP session
     * @param decisions map of toolCallId → confirmed
     * @return true if a pending future was found and resolved, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean resolveBatchConfirmation(HttpSession session, Map<String, Boolean> decisions) {
        CompletableFuture<Map<String, Boolean>> future =
                (CompletableFuture<Map<String, Boolean>>) session.getAttribute(SESSION_BATCH_CONFIRM_FUTURE);
        if (future != null) {
            session.removeAttribute(SESSION_BATCH_CONFIRM_FUTURE);
            future.complete(decisions);
            log.debug("Batch confirmation resolved ({} decisions) for session {}", 
                     decisions.size(), session.getId());
            return true;
        }
        log.warn("resolveBatchConfirmation called but no pending future for session {}", session.getId());
        return false;
    }
}
