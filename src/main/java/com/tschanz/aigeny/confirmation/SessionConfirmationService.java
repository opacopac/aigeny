package com.tschanz.aigeny.confirmation;

import jakarta.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Narrow session interface for Jira-confirmation future management (ISP, I-1).
 *
 * <p>Consumers that only need to create or resolve confirmation futures
 * depend on this interface instead of the broad {@link ChatSessionService}.
 * Covers both single-action and batch confirmations.
 */
public interface SessionConfirmationService {

    // ── Single confirmation ──────────────────────────────────────────────────

    /**
     * Creates and stores a new {@link CompletableFuture} for a single Jira confirmation.
     * The future will be resolved when the user clicks Confirm or Decline.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user decides
     */
    CompletableFuture<Boolean> createConfirmationFuture(HttpSession session);

    /**
     * Resolves the pending confirmation future with the user's decision.
     *
     * @param session   HTTP session
     * @param confirmed {@code true} if the user confirmed, {@code false} if declined
     * @return {@code true} if a pending future was found and resolved
     */
    boolean resolveConfirmation(HttpSession session, boolean confirmed);

    // ── Batch confirmation ───────────────────────────────────────────────────

    /**
     * Creates and stores a new {@link CompletableFuture} for a batch Jira confirmation dialog.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user submits all decisions
     */
    CompletableFuture<Map<String, Boolean>> createBatchConfirmationFuture(HttpSession session);

    /**
     * Resolves the pending batch confirmation future with the user's decisions.
     *
     * @param session   HTTP session
     * @param decisions map of toolCallId → confirmed
     * @return {@code true} if a pending future was found and resolved
     */
    boolean resolveBatchConfirmation(HttpSession session, Map<String, Boolean> decisions);
}

