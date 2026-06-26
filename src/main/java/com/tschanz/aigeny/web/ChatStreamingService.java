package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.jira.ConfirmationContext;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.orchestration.BatchConfirmationContext;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.CurrentToolCallContext;
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
 *
 * <p>Write tools that require user confirmation use a synchronous approach:
 * the tool sends a {@code confirmation_required} SSE event with the action description,
 * blocks the orchestration thread waiting for a {@link CompletableFuture} stored in the
 * HTTP session, and resumes once the user calls {@code POST /api/jira/confirm-decision}.
 */
@Service
public class ChatStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamingService.class);

    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes

    // SSE event types
    private static final String EVENT_TYPE_ERROR        = "error";
    private static final String EVENT_TYPE_TOOL_CALL    = "tool_call";
    private static final String EVENT_TYPE_INTERMEDIATE = "intermediate";
    private static final String EVENT_TYPE_DONE         = "done";
    private static final String EVENT_TYPE_CANCELLED    = "cancelled";

    // JSON keys
    private static final String KEY_TYPE       = "type";
    private static final String KEY_MESSAGE    = "message";
    private static final String KEY_TOOL_NAME  = "toolName";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_RESPONSE   = "response";
    private static final String KEY_HAS_EXPORT = "hasExport";

    private final OrchestrationService orchestration;
    private final ChatSessionService sessionService;
    private final ConfirmationOrchestrator confirmationOrchestrator;
    private final ObjectMapper objectMapper;

    public ChatStreamingService(OrchestrationService orchestration,
                                ChatSessionService sessionService,
                                ConfirmationOrchestrator confirmationOrchestrator,
                                ObjectMapper objectMapper) {
        this.orchestration            = orchestration;
        this.sessionService           = sessionService;
        this.confirmationOrchestrator = confirmationOrchestrator;
        this.objectMapper             = objectMapper;
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
                confirmationOrchestrator.handleSingleConfirmation(
                        emitter, session, jiraToken, jiraWriteEnabled, humanDescription, action));

        // Set up batch confirmation handler: when 2+ write tools appear in one LLM response,
        // show a single combined dialog instead of one per write tool.
        BatchConfirmationContext.set(writeToolInfos ->
                confirmationOrchestrator.handleBatchConfirmation(emitter, session, writeToolInfos));

        try {
            runOrchestrationAndComplete(emitter, message, history, session, cancelFlag);
        } finally {
            cleanup(session);
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
        ConfirmationContext.clearPreApproved();
        BatchConfirmationContext.clear();
    }
}
