package com.tschanz.aigeny.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.chat.ChatResult;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages Server-Sent Events (SSE) lifecycle and message sending.
 * 
 * <p>Handles:
 * <ul>
 *   <li>SSE emitter creation and configuration</li>
 *   <li>Sending structured SSE events (tool calls, intermediate messages, completion)</li>
 *   <li>Error handling and emitter completion</li>
 *   <li>Cancellation handling</li>
 * </ul>
 * 
 * <p>This service centralizes all SSE communication logic, providing a clean API
 * for sending different types of events during chat processing.
 */
@Service
public class SseStreamManager {

    private static final Logger log = LoggerFactory.getLogger(SseStreamManager.class);

    private static final long SSE_TIMEOUT_MS = 300_000L; // 5 minutes

    // SSE event types
    private static final String EVENT_TYPE_ERROR        = "error";
    private static final String EVENT_TYPE_TOOL_CALL    = "tool_call";
    private static final String EVENT_TYPE_INTERMEDIATE = "intermediate";
    private static final String EVENT_TYPE_DONE         = "done";
    private static final String EVENT_TYPE_CANCELLED    = "cancelled";

    // JSON keys
    private static final String KEY_TYPE        = "type";
    private static final String KEY_MESSAGE     = "message";
    private static final String KEY_TOOL_NAME   = "toolName";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_RESPONSE    = "response";
    private static final String KEY_HAS_EXPORT  = "hasExport";

    private final ObjectMapper objectMapper;

    public SseStreamManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new SSE emitter with configured timeout.
     * 
     * @return A new SseEmitter instance ready for streaming
     */
    public SseEmitter createEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    /**
     * Sends a tool call event to the client.
     * 
     * @param emitter    The SSE emitter to send to
     * @param toolName   The name of the tool being called
     * @param description A human-readable description of what the tool is doing
     */
    public void sendToolCall(SseEmitter emitter, String toolName, String description) {
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
     * Sends an intermediate message to the client during processing.
     * 
     * @param emitter         The SSE emitter to send to
     * @param intermediateText The intermediate response text to send
     */
    public void sendIntermediateMessage(SseEmitter emitter, String intermediateText) {
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
     * Sends the final completion event with the chat result and completes the emitter.
     * 
     * @param emitter The SSE emitter to send to
     * @param result  The chat result containing the final response and export data
     */
    public void sendCompletionAndClose(SseEmitter emitter, ChatResult result) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put(KEY_TYPE, EVENT_TYPE_DONE);
            payload.put(KEY_RESPONSE, result.response());
            payload.put(KEY_HAS_EXPORT, result.hasExportData());
            sendJson(emitter, payload);
            emitter.complete();
        } catch (Exception e) {
            log.error("SSE completion send failed", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * Handles cancellation by sending a cancellation event and completing the emitter.
     * 
     * @param emitter The SSE emitter to send to
     * @param session The HTTP session being cancelled
     */
    public void handleCancellation(SseEmitter emitter, HttpSession session) {
        log.info("Chat stream cancelled by user for session {}", session.getId());
        try {
            sendJson(emitter, Map.of(KEY_TYPE, EVENT_TYPE_CANCELLED));
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    /**
     * Handles errors by sending an error event and completing the emitter.
     * 
     * @param emitter The SSE emitter to send to
     * @param e       The exception that occurred
     */
    public void handleError(SseEmitter emitter, Exception e) {
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
     * Sends an error message and completes the emitter asynchronously.
     * Used for validation errors before processing starts.
     * 
     * @param emitter      The SSE emitter to send to
     * @param errorMessage The error message to send
     */
    public void sendErrorAndComplete(SseEmitter emitter, String errorMessage) {
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
     * Sends a JSON payload to the SSE emitter.
     * 
     * @param emitter The SSE emitter to send to
     * @param payload The data to serialize and send
     * @throws Exception If JSON serialization or SSE sending fails
     */
    private void sendJson(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().data(json));
    }
}
