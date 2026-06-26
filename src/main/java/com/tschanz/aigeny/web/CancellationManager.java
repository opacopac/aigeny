package com.tschanz.aigeny.web;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages cancellation flags for ongoing chat operations.
 * 
 * <p>When a user starts a chat request, a cancel flag is created and stored in the session.
 * The orchestration thread periodically checks this flag to see if the user has requested
 * cancellation (e.g., by clicking a "Cancel" button in the UI).
 * 
 * <p>The flag is implemented as an {@link AtomicBoolean} to ensure thread-safe access
 * between the orchestration thread and the HTTP request thread that handles the cancel
 * endpoint.
 * 
 * <p>Flags are automatically cleaned up after each chat operation completes.
 */
@Service
public class CancellationManager {

    private static final Logger log = LoggerFactory.getLogger(CancellationManager.class);

    // Session attribute key
    private static final String SESSION_CANCEL_FLAG = "chatCancelFlag";

    /**
     * Creates and stores a new cancel flag for the current chat operation.
     * The flag starts in the "not cancelled" state (false).
     *
     * @param session HTTP session
     * @return the created cancel flag that can be checked during orchestration
     */
    public AtomicBoolean createCancelFlag(HttpSession session) {
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        session.setAttribute(SESSION_CANCEL_FLAG, cancelFlag);
        log.debug("Created cancel flag for session {}", session.getId());
        return cancelFlag;
    }

    /**
     * Retrieves the cancel flag for the current chat operation.
     *
     * @param session HTTP session
     * @return cancel flag or null if none exists (no active chat operation)
     */
    public AtomicBoolean getCancelFlag(HttpSession session) {
        return (AtomicBoolean) session.getAttribute(SESSION_CANCEL_FLAG);
    }

    /**
     * Triggers cancellation by setting the cancel flag to true.
     * The orchestration thread will detect this and stop processing.
     *
     * @param session HTTP session
     * @return true if a cancel flag was found and triggered, false otherwise
     */
    public boolean triggerCancellation(HttpSession session) {
        AtomicBoolean flag = getCancelFlag(session);
        if (flag != null) {
            flag.set(true);
            log.info("Chat cancellation triggered for session {}", session.getId());
            return true;
        }
        log.warn("Cancellation requested but no active chat operation for session {}", session.getId());
        return false;
    }

    /**
     * Removes the cancel flag from the session.
     * Should be called after a chat operation completes (whether successful, cancelled, or failed).
     *
     * @param session HTTP session
     */
    public void clearCancelFlag(HttpSession session) {
        session.removeAttribute(SESSION_CANCEL_FLAG);
        log.debug("Cleared cancel flag for session {}", session.getId());
    }

    /**
     * Checks if cancellation has been requested for the current operation.
     * Convenience method for use in orchestration loops.
     *
     * @param session HTTP session
     * @return true if cancellation was requested, false otherwise
     */
    public boolean isCancellationRequested(HttpSession session) {
        AtomicBoolean flag = getCancelFlag(session);
        return flag != null && flag.get();
    }
}
