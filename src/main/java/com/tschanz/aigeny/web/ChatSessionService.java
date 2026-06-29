package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.tool.jira.PendingJiraAction;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages chat session state including conversation history, query results,
 * pending actions, and cancellation flags.
 *
 * <p>Implements five narrow ISP interfaces so that each consumer can depend
 * only on the subset of session operations it actually needs (I-1 refactoring):
 * <ul>
 *   <li>{@link SessionHistoryService}     – history read / clear</li>
 *   <li>{@link SessionExportService}      – query-result export</li>
 *   <li>{@link SessionCancellationService}– cancel flag lifecycle</li>
 *   <li>{@link SessionConfirmationService}– single and batch confirmation futures</li>
 *   <li>{@link SessionJiraWriteService}   – Jira write-mode flag</li>
 * </ul>
 */
@Service
public class ChatSessionService implements
        SessionHistoryService,
        SessionExportService,
        SessionCancellationService,
        SessionConfirmationService,
        SessionJiraWriteService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final ConfirmationFutureManager confirmationFutureManager;
    private final CancellationManager cancellationManager;
    private final HistoryManager historyManager;

    // Session attribute keys
    private static final String SESSION_RESULT               = "lastQueryResult";
    private static final String SESSION_PENDING_ACTION       = "pendingJiraAction";
    private static final String SESSION_JIRA_WRITE           = "jiraWriteEnabled";

    public ChatSessionService(ConfirmationFutureManager confirmationFutureManager,
                              CancellationManager cancellationManager,
                              HistoryManager historyManager) {
        this.confirmationFutureManager = confirmationFutureManager;
        this.cancellationManager = cancellationManager;
        this.historyManager = historyManager;
    }

    // ── Chat History Management ──────────────────────────────────────────────

    /**
     * Retrieves the chat history for the session, creating a new one if it doesn't exist.
     *
     * @param session HTTP session
     * @return mutable list of messages
     */
    public List<Message> getOrCreateHistory(HttpSession session) {
        return historyManager.getOrCreateHistory(session);
    }

    /**
     * Clears the chat history for the session.
     *
     * @param session HTTP session
     */
    public void clearHistory(HttpSession session) {
        historyManager.clearHistory(session);
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
     * Delegates to {@link CancellationManager}.
     *
     * @param session HTTP session
     * @return the created cancel flag
     */
    public AtomicBoolean createCancelFlag(HttpSession session) {
        return cancellationManager.createCancelFlag(session);
    }

    /**
     * Retrieves the cancel flag for the current chat operation.
     * Delegates to {@link CancellationManager}.
     *
     * @param session HTTP session
     * @return cancel flag or null if none exists
     */
    public AtomicBoolean getCancelFlag(HttpSession session) {
        return cancellationManager.getCancelFlag(session);
    }

    /**
     * Triggers cancellation by setting the cancel flag to true.
     * Delegates to {@link CancellationManager}.
     *
     * @param session HTTP session
     * @return true if a cancel flag was found and triggered, false otherwise
     */
    public boolean triggerCancellation(HttpSession session) {
        return cancellationManager.triggerCancellation(session);
    }

    /**
     * Removes the cancel flag from the session.
     * Delegates to {@link CancellationManager}.
     *
     * @param session HTTP session
     */
    public void clearCancelFlag(HttpSession session) {
        cancellationManager.clearCancelFlag(session);
    }

    // ── Confirmation Future Management ───────────────────────────────────────

    /**
     * Creates a new CompletableFuture<Boolean> for a synchronous Jira confirmation.
     * Delegates to {@link ConfirmationFutureManager}.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user decides
     */
    public CompletableFuture<Boolean> createConfirmationFuture(HttpSession session) {
        return confirmationFutureManager.createSingleConfirmationFuture(session);
    }

    /**
     * Resolves the pending confirmation future with the user's decision.
     * Delegates to {@link ConfirmationFutureManager}.
     *
     * @param session   HTTP session
     * @param confirmed true if the user confirmed, false if declined
     * @return true if a pending future was found and resolved, false otherwise
     */
    public boolean resolveConfirmation(HttpSession session, boolean confirmed) {
        return confirmationFutureManager.resolveSingleConfirmation(session, confirmed);
    }

    // ── Batch Confirmation Future Management ─────────────────────────────────

    /**
     * Creates a new CompletableFuture for a batch Jira confirmation dialog.
     * Delegates to {@link ConfirmationFutureManager}.
     *
     * @param session HTTP session
     * @return the future that will be completed when the user submits all decisions
     */
    public CompletableFuture<Map<String, Boolean>> createBatchConfirmationFuture(HttpSession session) {
        return confirmationFutureManager.createBatchConfirmationFuture(session);
    }

    /**
     * Resolves the pending batch confirmation future with the user's decisions.
     * Delegates to {@link ConfirmationFutureManager}.
     *
     * @param session   HTTP session
     * @param decisions map of toolCallId → confirmed
     * @return true if a pending future was found and resolved, false otherwise
     */
    public boolean resolveBatchConfirmation(HttpSession session, Map<String, Boolean> decisions) {
        return confirmationFutureManager.resolveBatchConfirmation(session, decisions);
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

