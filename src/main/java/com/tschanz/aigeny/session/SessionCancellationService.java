package com.tschanz.aigeny.session;

import jakarta.servlet.http.HttpSession;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Narrow session interface for chat-cancellation management (ISP, I-1).
 *
 * <p>Consumers that only need to create, trigger, or clear a cancel flag
 * depend on this interface instead of the broad {@link ChatSessionService}.
 */
public interface SessionCancellationService {

    /**
     * Creates and stores a fresh cancel flag for the current chat operation.
     *
     * @param session HTTP session
     * @return the newly created flag (always {@code false} initially)
     */
    AtomicBoolean createCancelFlag(HttpSession session);

    /**
     * Returns the active cancel flag for the session, or {@code null} if none exists.
     *
     * @param session HTTP session
     */
    AtomicBoolean getCancelFlag(HttpSession session);

    /**
     * Sets the cancel flag to {@code true}, signalling the running stream to stop.
     *
     * @param session HTTP session
     * @return {@code true} if a flag was found and triggered, {@code false} otherwise
     */
    boolean triggerCancellation(HttpSession session);

    /**
     * Removes the cancel flag from the session.
     *
     * @param session HTTP session
     */
    void clearCancelFlag(HttpSession session);
}

