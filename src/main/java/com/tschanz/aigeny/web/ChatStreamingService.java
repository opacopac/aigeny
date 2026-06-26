package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteExecutor;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles Server-Sent Events (SSE) streaming for chat responses.
 * Manages the lifecycle of SSE emitters and sends real-time updates during chat processing.
 */
@Service
public class ChatStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);

    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes

    // SSE event types
    private static final String EVENT_TYPE_ERROR = "error";
    private static final String EVENT_TYPE_TOOL_CALL = "tool_call";
    private static final String EVENT_TYPE_INTERMEDIATE = "intermediate";
    private static final String EVENT_TYPE_DONE = "done";
    private static final String EVENT_TYPE_CANCELLED = "cancelled";

    // JSON keys
    private static final String KEY_TYPE = "type";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_TOOL_NAME = "toolName";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_RESPONSE = "response";
    private static final String KEY_HAS_EXPORT = "hasExport";
    private static final String KEY_PENDING_ACTION = "pendingAction";
    private static final String KEY_ISSUE_KEY = "issueKey";

    // Message keys
    private static final String MSG_JIRA_WRITE_ERROR   = "chat.jira.write_error";
    private static final String MSG_JIRA_CONTINUATION  = "jira.confirm.continuation";

    private final OrchestrationService orchestration;
    private final ChatSessionService sessionService;
    private final JiraWriteExecutor jiraWriteExecutor;
    private final ObjectMapper objectMapper;

    public ChatStreamingService(OrchestrationService orchestration,
                                ChatSessionService sessionService,
                                JiraWriteExecutor jiraWriteExecutor,
                                ObjectMapper objectMapper) {
        this.orchestration     = orchestration;
        this.sessionService    = sessionService;
        this.jiraWriteExecutor = jiraWriteExecutor;
        this.objectMapper      = objectMapper;
    }

    /**
     * Creates and configures an SSE emitter for streaming chat responses.
     *
     * @param message user message
     * @param history conversation history
     * @param session HTTP session
     * @param jiraToken effective Jira token
     * @param jiraWriteEnabled whether Jira write mode is enabled
     * @param bitbucketToken effective Bitbucket token
     * @return configured SSE emitter
     */
    public SseEmitter streamChat(String message,
                                  List<Message> history,
                                  HttpSession session,
                                  String jiraToken,
                                  boolean jiraWriteEnabled,
                                  String bitbucketToken) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Validate message
        if (message == null || message.trim().isEmpty()) {
            sendErrorAndComplete(emitter, Messages.get("chat.error.empty_message"));
            return emitter;
        }

        // Setup cancellation flag
        AtomicBoolean cancelFlag = sessionService.createCancelFlag(session);
        emitter.onCompletion(() -> cancelFlag.set(true));
        emitter.onTimeout(() -> cancelFlag.set(true));
        emitter.onError(t -> cancelFlag.set(true));

        // Process chat asynchronously
        CompletableFuture.runAsync(() -> processChatStream(
                emitter, message, history, session, jiraToken, jiraWriteEnabled, bitbucketToken, cancelFlag
        ));

        return emitter;
    }

    /**
     * After the user confirms pending Jira actions: executes them, sends the result
     * as an SSE intermediate message, then resumes the LLM so it can continue any
     * remaining steps (e.g. renaming cloned sub-tasks).
     *
     * @param pendingActions actions to execute (already removed from session by caller)
     * @param history        current conversation history
     * @param session        HTTP session
     * @param jiraToken      effective Jira PAT
     * @param jiraWriteEnabled whether Jira write mode is enabled
     * @param bitbucketToken effective Bitbucket PAT
     * @return configured SSE emitter
     */
    public SseEmitter streamAfterConfirmation(List<PendingJiraAction> pendingActions,
                                              List<Message> history,
                                              HttpSession session,
                                              String jiraToken,
                                              boolean jiraWriteEnabled,
                                              String bitbucketToken) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        AtomicBoolean cancelFlag = sessionService.createCancelFlag(session);
        emitter.onCompletion(() -> cancelFlag.set(true));
        emitter.onTimeout(() -> cancelFlag.set(true));
        emitter.onError(t -> cancelFlag.set(true));

        CompletableFuture.runAsync(() -> processConfirmationStream(
                emitter, pendingActions, history, session,
                jiraToken, jiraWriteEnabled, bitbucketToken, cancelFlag));

        return emitter;
    }

    /**
     * Async body of {@link #streamAfterConfirmation}.
     * 1. Executes all pending Jira actions synchronously in this thread.
     * 2. Sends the aggregated result as an SSE {@code intermediate} event.
     * 3. Continues the LLM conversation so it can finish any remaining planned steps.
     */
    private void processConfirmationStream(SseEmitter emitter,
                                           List<PendingJiraAction> pendingActions,
                                           List<Message> history,
                                           HttpSession session,
                                           String jiraToken,
                                           boolean jiraWriteEnabled,
                                           String bitbucketToken,
                                           AtomicBoolean cancelFlag) {

        // Execute Jira actions (write context needed by JiraWriteExecutor)
        JiraWriteContext.set(jiraWriteEnabled);
        StringBuilder resultBuilder = new StringBuilder();
        for (PendingJiraAction action : pendingActions) {
            try {
                String res = jiraWriteExecutor.execute(action, jiraToken);
                resultBuilder.append(res).append("\n");
            } catch (Exception e) {
                log.error("Jira write failed for action {}", action.getActionType(), e);
                resultBuilder.append(Messages.get(MSG_JIRA_WRITE_ERROR, e.getMessage())).append("\n");
            }
        }
        JiraWriteContext.clear();
        String confirmationResult = resultBuilder.toString().trim();

        // Show execution result in chat before the LLM continuation starts
        sendIntermediateMessage(emitter, confirmationResult);

        // Build the synthetic user message that resumes the LLM plan
        String continuationMsg = Messages.get(MSG_JIRA_CONTINUATION, confirmationResult);

        // Continue with a normal LLM orchestration turn
        JiraTokenContext.set(jiraToken);
        JiraWriteContext.set(jiraWriteEnabled);
        BitbucketTokenContext.set(bitbucketToken);
        PendingJiraActionContext.clear();

        try {
            runOrchestrationAndComplete(emitter, continuationMsg, history, session, cancelFlag);
        } finally {
            cleanup(session);
        }
    }

    /**
     * Processes the chat stream asynchronously.
     */
    private void processChatStream(SseEmitter emitter,
                                    String message,
                                    List<Message> history,
                                    HttpSession session,
                                    String jiraToken,
                                    boolean jiraWriteEnabled,
                                    String bitbucketToken,
                                    AtomicBoolean cancelFlag) {

        // Set ThreadLocal contexts
        JiraTokenContext.set(jiraToken);
        JiraWriteContext.set(jiraWriteEnabled);
        BitbucketTokenContext.set(bitbucketToken);
        PendingJiraActionContext.clear();

        // Guard (defence-in-depth): if the UI somehow allowed a new turn while a
        // Jira confirmation was still pending, drop the stale actions so the
        // session never holds an orphaned batch that can no longer be confirmed.
        if (sessionService.hasPendingJiraActions(session)) {
            log.warn("New chat turn started for session {} while Jira confirmation was " +
                     "still pending – clearing stale pending actions", session.getId());
            sessionService.clearPendingJiraActions(session);
        }

        try {
            runOrchestrationAndComplete(emitter, message, history, session, cancelFlag);
        } finally {
            cleanup(session);
        }
    }

    /**
     * Shared core: runs LLM orchestration and sends the final SSE done event.
     * ThreadLocal contexts must already be set by the caller.
     * Does NOT call {@link #cleanup} – that is the caller's responsibility.
     */
    private void runOrchestrationAndComplete(SseEmitter emitter,
                                             String message,
                                             List<Message> history,
                                             HttpSession session,
                                             AtomicBoolean cancelFlag) {
        try {
            ChatResult result = orchestration.chat(
                    history,
                    message,
                    (toolName, description) -> sendToolCall(emitter, toolName, description),
                    (intermediateText) -> sendIntermediateMessage(emitter, intermediateText),
                    cancelFlag::get
            );

            if (result.hasExportData()) {
                sessionService.setLastQueryResult(session, result.lastToolResult().getQueryResult());
            }

            Map<String, Object> payload = buildDonePayload(result, session);
            sendJson(emitter, payload);
            emitter.complete();

        } catch (InterruptedException ie) {
            handleCancellation(emitter, session);
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }

    /**
     * Builds the final "done" payload with chat result and pending actions.
     */
    private Map<String, Object> buildDonePayload(ChatResult result, HttpSession session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(KEY_TYPE, EVENT_TYPE_DONE);
        payload.put(KEY_RESPONSE, result.response());
        payload.put(KEY_HAS_EXPORT, result.hasExportData());

        // Add pending Jira actions if any
        List<PendingJiraAction> pendingActions = PendingJiraActionContext.get();
        if (pendingActions != null && !pendingActions.isEmpty()) {
            sessionService.setPendingJiraActions(session, pendingActions);
            payload.put(KEY_PENDING_ACTION, buildPendingActionPayload(pendingActions));
        }

        return payload;
    }

    /**
     * Builds the pending action payload for confirmation dialog.
     */
    private Map<String, Object> buildPendingActionPayload(List<PendingJiraAction> pendingActions) {
        StringBuilder combinedDesc = new StringBuilder();

        if (pendingActions.size() == 1) {
            combinedDesc.append(pendingActions.get(0).getHumanDescription());
        } else {
            combinedDesc.append("**").append(pendingActions.size())
                    .append(" Aktionen werden ausgeführt:**\n\n");
            for (int i = 0; i < pendingActions.size(); i++) {
                combinedDesc.append("**").append(i + 1).append(".** ")
                        .append(pendingActions.get(i).getHumanDescription())
                        .append("\n\n");
            }
        }

        Map<String, Object> pendingPayload = new HashMap<>();
        pendingPayload.put(KEY_DESCRIPTION, combinedDesc.toString().trim());
        pendingPayload.put(KEY_ISSUE_KEY, "");
        return pendingPayload;
    }

    /**
     * Sends a tool call event to the client.
     */
    private void sendToolCall(SseEmitter emitter, String toolName, String description) {
        try {
            Map<String, Object> event = Map.of(
                    KEY_TYPE, EVENT_TYPE_TOOL_CALL,
                    KEY_TOOL_NAME, toolName,
                    KEY_DESCRIPTION, description
            );
            sendJson(emitter, event);
        } catch (Exception e) {
            log.warn("SSE tool_call send failed: {}", e.getMessage());
        }
    }

    /**
     * Sends an intermediate message event to the client.
     */
    private void sendIntermediateMessage(SseEmitter emitter, String intermediateText) {
        try {
            Map<String, Object> event = Map.of(
                    KEY_TYPE, EVENT_TYPE_INTERMEDIATE,
                    KEY_RESPONSE, intermediateText
            );
            sendJson(emitter, event);
        } catch (Exception e) {
            log.warn("SSE intermediate send failed: {}", e.getMessage());
        }
    }

    /**
     * Handles cancellation by user.
     */
    private void handleCancellation(SseEmitter emitter, HttpSession session) {
        log.info("Chat stream cancelled by user for session {}", session.getId());
        try {
            sendJson(emitter, Map.of(KEY_TYPE, EVENT_TYPE_CANCELLED));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    /**
     * Handles errors during chat processing.
     */
    private void handleError(SseEmitter emitter, Exception e) {
        log.error("Chat stream error", e);
        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        try {
            Map<String, Object> errorEvent = Map.of(
                    KEY_TYPE, EVENT_TYPE_ERROR,
                    KEY_MESSAGE, errMsg
            );
            sendJson(emitter, errorEvent);
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    /**
     * Sends an error event and completes the emitter.
     */
    private void sendErrorAndComplete(SseEmitter emitter, String errorMessage) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> errorEvent = Map.of(
                        KEY_TYPE, EVENT_TYPE_ERROR,
                        KEY_MESSAGE, errorMessage
                );
                sendJson(emitter, errorEvent);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
    }

    /**
     * Sends a JSON payload as SSE event.
     */
    private void sendJson(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().data(json));
    }

    /**
     * Cleans up ThreadLocal contexts and session state.
     */
    private void cleanup(HttpSession session) {
        sessionService.clearCancelFlag(session);
        JiraTokenContext.clear();
        JiraWriteContext.clear();
        BitbucketTokenContext.clear();
        PendingJiraActionContext.clear();
    }
}

