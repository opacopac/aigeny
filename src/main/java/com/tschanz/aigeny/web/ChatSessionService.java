package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages chat session state including conversation history, query results,
 * pending actions, and cancellation flags.
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    // Session attribute keys
    private static final String SESSION_HISTORY             = "chatHistory";
    private static final String SESSION_RESULT              = "lastQueryResult";
    private static final String SESSION_PENDING_ACTION      = "pendingJiraAction";
    private static final String SESSION_JIRA_WRITE          = "jiraWriteEnabled";
    private static final String SESSION_CANCEL_FLAG         = "chatCancelFlag";
    private static final String SESSION_CONFIRMATION_FUTURE = "jiraConfirmationFuture";

    // ── Chat History Management ──────────────────────────────────────────────

    /**
     * Retrieves the chat history for the session, creating a new one if it doesn't exist.
     *
     * @param session HTTP session
     * @return mutable list of messages
     */
    @SuppressWarnings("unchecked")
    public List<Message> getOrCreateHistory(HttpSession session) {
        List<Message> history = (List<Message>) session.getAttribute(SESSION_HISTORY);
        if (history == null) {
            history = new ArrayList<>();
            session.setAttribute(SESSION_HISTORY, history);
            log.debug("Created new chat history for session {}", session.getId());
        }
        return history;
    }

    /**
     * Clears the chat history for the session.
     *
     * @param session HTTP session
     */
    public void clearHistory(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
        log.info("Chat history cleared for session {}", session.getId());
    }

    // ── Query Result Management ──────────────────────────────────────────────

    /**
     * Stores a query result in the session (for export functionality).
     *
     * @param session HTTP session
     * @param result query result to store
     */
    public void setLastQueryResult(HttpSession session, QueryResult result) {
        session.setAttribute(SESSION_RESULT, result);
        log.debug("Stored query result for session {} ({} rows)",
                 session.getId(), result != null ? result.getRows().size() : 0);
    }

    /**
     * Retrieves the last query result from the session.
     *
     * @param session HTTP session
     * @return last query result or null if none exists
     */
    public QueryResult getLastQueryResult(HttpSession session) {
        return (QueryResult) session.getAttribute(SESSION_RESULT);
    }

    /**
     * Checks if a query result is available in the session.
     *
     * @param session HTTP session
     * @return true if a query result exists and is not empty
     */
    public boolean hasQueryResult(HttpSession session) {
        QueryResult result = getLastQueryResult(session);
        return result != null && !result.isEmpty();
    }

    /**
     * Clears the last query result from the session.
     *
     * @param session HTTP session
     */
    public void clearLastQueryResult(HttpSession session) {
        session.removeAttribute(SESSION_RESULT);
        log.debug("Cleared query result for session {}", session.getId());
    }

    // ── Pending Jira Actions Management ──────────────────────────────────────

    /**
     * Stores pending Jira actions in the session.
     *
     * @param session HTTP session
     * @param actions list of pending actions
     */
    public void setPendingJiraActions(HttpSession session, List<PendingJiraAction> actions) {
        session.setAttribute(SESSION_PENDING_ACTION, actions);
        log.debug("Stored {} pending Jira action(s) for session {}",
                 actions != null ? actions.size() : 0, session.getId());
    }

    /**
     * Retrieves pending Jira actions from the session.
     *
     * @param session HTTP session
     * @return list of pending actions or null if none exist
     */
    @SuppressWarnings("unchecked")
    public List<PendingJiraAction> getPendingJiraActions(HttpSession session) {
        return (List<PendingJiraAction>) session.getAttribute(SESSION_PENDING_ACTION);
    }

    /**
     * Checks if there are pending Jira actions in the session.
     *
     * @param session HTTP session
     * @return true if pending actions exist
     */
    public boolean hasPendingJiraActions(HttpSession session) {
        List<PendingJiraAction> actions = getPendingJiraActions(session);
        return actions != null && !actions.isEmpty();
    }

    /**
     * Clears pending Jira actions from the session.
     *
     * @param session HTTP session
     */
    public void clearPendingJiraActions(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_ACTION);
        log.debug("Cleared pending Jira actions for session {}", session.getId());
    }

    // ── Jira Write Mode Management ───────────────────────────────────────────

    /**
     * Enables or disables Jira write mode for the session.
     *
     * @param session HTTP session
     * @param enabled true to enable write mode, false to disable
     */
    public void setJiraWriteMode(HttpSession session, boolean enabled) {
        session.setAttribute(SESSION_JIRA_WRITE, enabled);
        log.info("Jira write mode {} for session {}",
                enabled ? "enabled" : "disabled", session.getId());
    }

    /**
     * Checks if Jira write mode is enabled for the session.
     *
     * @param session HTTP session
     * @return true if write mode is enabled
     */
    public boolean isJiraWriteModeEnabled(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(SESSION_JIRA_WRITE));
    }

    // ── Cancel Flag Management ───────────────────────────────────────────────

    /**
     * Creates and stores a new cancel flag for the current chat operation.
     *
     * @param session HTTP session
     * @return the created cancel flag
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
     * @return cancel flag or null if none exists
     */
    public AtomicBoolean getCancelFlag(HttpSession session) {
        return (AtomicBoolean) session.getAttribute(SESSION_CANCEL_FLAG);
    }

    /**
     * Triggers cancellation by setting the cancel flag to true.
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
        return false;
    }

    /**
     * Removes the cancel flag from the session.
     *
     * @param session HTTP session
     */
    public void clearCancelFlag(HttpSession session) {
        session.removeAttribute(SESSION_CANCEL_FLAG);
        log.debug("Cleared cancel flag for session {}", session.getId());
    }

    // ── Confirmation Future Management ───────────────────────────────────────

    /**
     * Creates a new CompletableFuture<Boolean> for a synchronous Jira confirmation.
     * Stored in the session so the /api/jira/confirm-decision endpoint can resolve it.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user decides
     */
    public CompletableFuture<Boolean> createConfirmationFuture(HttpSession session) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        session.setAttribute(SESSION_CONFIRMATION_FUTURE, future);
        log.debug("Created confirmation future for session {}", session.getId());
        return future;
    }

    /**
     * Resolves the pending confirmation future with the user's decision.
     * Removes it from the session after resolving.
     *
     * @param session   HTTP session
     * @param confirmed true if the user confirmed, false if declined
     * @return true if a pending future was found and resolved, false otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean resolveConfirmation(HttpSession session, boolean confirmed) {
        CompletableFuture<Boolean> future =
                (CompletableFuture<Boolean>) session.getAttribute(SESSION_CONFIRMATION_FUTURE);
        if (future != null) {
            session.removeAttribute(SESSION_CONFIRMATION_FUTURE);
            future.complete(confirmed);
            log.debug("Confirmation resolved ({}) for session {}", confirmed ? "confirmed" : "declined", session.getId());
            return true;
        }
        log.warn("resolveConfirmation called but no pending future for session {}", session.getId());
        return false;
    }

    // ── Session Cleanup ──────────────────────────────────────────────────────

    /**
     * Clears all chat-related data from the session.
     *
     * @param session HTTP session
     */
    public void clearAll(HttpSession session) {
        clearHistory(session);
        clearLastQueryResult(session);
        clearPendingJiraActions(session);
        clearCancelFlag(session);
        log.info("All chat data cleared for session {}", session.getId());
    }
}

