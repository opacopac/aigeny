package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.jira.ConfirmationContext;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles Server-Sent Events (SSE) streaming for chat responses.
 * Manages the lifecycle of SSE emitters and sends real-time updates during chat processing.
 *
 * <p>Write tools that require user confirmation use a synchronous approach:
 * the tool sends a {@code confirmation_required} SSE event with the action description,
 * blocks the orchestration thread waiting for a {@link CompletableFuture} stored in the
 * HTTP session, and resumes once the user calls {@code POST /api/jira/confirm-decision}.
 */
@Service
public class ChatStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);

    private static final long SSE_TIMEOUT_MS          = 300_000L; // 5 minutes
    private static final long CONFIRMATION_TIMEOUT_MIN = 5L;

    // SSE event types
    private static final String EVENT_TYPE_ERROR                = "error";
    private static final String EVENT_TYPE_TOOL_CALL            = "tool_call";
    private static final String EVENT_TYPE_INTERMEDIATE         = "intermediate";
    private static final String EVENT_TYPE_DONE                 = "done";
    private static final String EVENT_TYPE_CANCELLED            = "cancelled";
    private static final String EVENT_TYPE_CONFIRMATION_REQUIRED = "confirmation_required";

    // JSON keys
    private static final String KEY_TYPE        = "type";
    private static final String KEY_MESSAGE     = "message";
    private static final String KEY_TOOL_NAME   = "toolName";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_RESPONSE    = "response";
    private static final String KEY_HAS_EXPORT  = "hasExport";

    // Message keys
    private static final String MSG_JIRA_WRITE_ERROR       = "chat.jira.write_error";
    private static final String MSG_CONFIRMATION_DECLINED  = "jira.confirmation.declined";
    private static final String MSG_CONFIRMATION_TIMEOUT   = "jira.confirmation.timeout";

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
     */
    public SseEmitter streamChat(String message,
                                  List<Message> history,
                                  HttpSession session,
                                  String jiraToken,
                                  boolean jiraWriteEnabled,
                                  String bitbucketToken) {

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (message == null || message.trim().isEmpty()) {
            sendErrorAndComplete(emitter, Messages.get("chat.error.empty_message"));
            return emitter;
        }

        AtomicBoolean cancelFlag = sessionService.createCancelFlag(session);
        emitter.onCompletion(() -> cancelFlag.set(true));
        emitter.onTimeout(() -> cancelFlag.set(true));
        emitter.onError(t -> cancelFlag.set(true));

        CompletableFuture.runAsync(() -> processChatStream(
                emitter, message, history, session, jiraToken, jiraWriteEnabled, bitbucketToken, cancelFlag
        ));

        return emitter;
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

        JiraTokenContext.set(jiraToken);
        JiraWriteContext.set(jiraWriteEnabled);
        BitbucketTokenContext.set(bitbucketToken);
        PendingJiraActionContext.clear();

        // Set up synchronous confirmation handler for write tools
        ConfirmationContext.set((humanDescription, action) ->
                handleConfirmation(emitter, session, jiraToken, jiraWriteEnabled, humanDescription, action));

        try {
            runOrchestrationAndComplete(emitter, message, history, session, cancelFlag);
        } finally {
            cleanup(session);
        }
    }

    /**
     * Synchronous confirmation handler called by write tools that need user approval.
     *
     * <ol>
     *   <li>Sends a {@code confirmation_required} SSE event with the action description.</li>
     *   <li>Stores a {@link CompletableFuture} in the session and blocks until resolved.</li>
     *   <li>If confirmed: executes the Jira action and returns the result as a ToolResult
     *       (also sends an {@code intermediate} SSE event so the result appears in chat).</li>
     *   <li>If declined: returns a "declined" ToolResult without executing.</li>
     * </ol>
     */
    private ToolResult handleConfirmation(SseEmitter emitter,
                                          HttpSession session,
                                          String jiraToken,
                                          boolean jiraWriteEnabled,
                                          String humanDescription,
                                          PendingJiraAction action) {
        sendConfirmationRequired(emitter, humanDescription);

        CompletableFuture<Boolean> future = sessionService.createConfirmationFuture(session);
        try {
            boolean confirmed = future.get(CONFIRMATION_TIMEOUT_MIN, TimeUnit.MINUTES);
            if (confirmed) {
                JiraWriteContext.set(jiraWriteEnabled);
                try {
                    String result = jiraWriteExecutor.execute(action, jiraToken);
                    sendIntermediateMessage(emitter, result);
                    return new ToolResult(result);
                } catch (Exception e) {
                    log.error("Jira action execution failed after confirmation", e);
                    String errMsg = Messages.get(MSG_JIRA_WRITE_ERROR, e.getMessage());
                    return new ToolResult(errMsg);
                }
            } else {
                return new ToolResult(Messages.get(MSG_CONFIRMATION_DECLINED));
            }
        } catch (TimeoutException e) {
            log.warn("Confirmation timed out for session {}", session.getId());
            return new ToolResult(Messages.get(MSG_CONFIRMATION_TIMEOUT));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("Confirmation error: " + e.getMessage());
        }
    }

    /**
     * Shared core: runs LLM orchestration and sends the final SSE done event.
     * ThreadLocal contexts must already be set by the caller.
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

            Map<String, Object> payload = new HashMap<>();
            payload.put(KEY_TYPE, EVENT_TYPE_DONE);
            payload.put(KEY_RESPONSE, result.response());
            payload.put(KEY_HAS_EXPORT, result.hasExportData());
            sendJson(emitter, payload);
            emitter.complete();

        } catch (InterruptedException ie) {
            handleCancellation(emitter, session);
        } catch (Exception e) {
            handleError(emitter, e);
        }
    }

    // ── SSE helpers ───────────────────────────────────────────────────────────

    private void sendConfirmationRequired(SseEmitter emitter, String description) {
        try {
            Map<String, Object> event = Map.of(
                    KEY_TYPE, EVENT_TYPE_CONFIRMATION_REQUIRED,
                    KEY_DESCRIPTION, description
            );
            sendJson(emitter, event);
        } catch (Exception e) {
            log.warn("SSE confirmation_required send failed: {}", e.getMessage());
        }
    }

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

    private void handleCancellation(SseEmitter emitter, HttpSession session) {
        log.info("Chat stream cancelled by user for session {}", session.getId());
        try {
            sendJson(emitter, Map.of(KEY_TYPE, EVENT_TYPE_CANCELLED));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

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

    private void sendJson(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().data(json));
    }

    private void cleanup(HttpSession session) {
        sessionService.clearCancelFlag(session);
        JiraTokenContext.clear();
        JiraWriteContext.clear();
        BitbucketTokenContext.clear();
        PendingJiraActionContext.clear();
        ConfirmationContext.clear();
    }
}
