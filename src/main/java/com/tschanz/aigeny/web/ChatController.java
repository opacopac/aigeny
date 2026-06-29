package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.Messages;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for chat conversation endpoints and system status.
 *
 * <p>Owns: POST /api/chat, POST /api/chat/stream, POST /api/chat/cancel,
 * POST /api/chat/clear, GET /api/status
 *
 * <p>Token management → {@link TokenController}
 * <p>Jira confirmations → {@link ConfirmationController}
 * <p>Schema operations → {@link SchemaController}
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // ── JSON request body keys ───────────────────────────────────────────────
    private static final String REQ_MESSAGE = "message";

    // ── JSON response keys ───────────────────────────────────────────────────
    private static final String KEY_ERROR      = "error";
    private static final String KEY_RESPONSE   = "response";
    private static final String KEY_HAS_EXPORT = "hasExport";
    private static final String KEY_STATUS     = "status";

    // ── JSON response values ─────────────────────────────────────────────────
    private static final String VAL_OK = "ok";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_ERROR_EMPTY_MESSAGE = "chat.error.empty_message";
    private static final String MSG_ERROR_GENERIC       = "chat.error.generic";
    private static final String MSG_STATUS_CLEARED      = "chat.status.cleared";

    private final OrchestrationService orchestration;
    private final TokenService tokenService;
    private final ChatSessionService sessionService;
    private final StatusAggregatorService statusAggregator;
    private final ChatStreamingService streamingService;
    private final ExecutionContextManager contextManager;

    public ChatController(OrchestrationService orchestration,
                          TokenService tokenService,
                          ChatSessionService sessionService,
                          StatusAggregatorService statusAggregator,
                          ChatStreamingService streamingService,
                          ExecutionContextManager contextManager) {
        this.orchestration    = orchestration;
        this.tokenService     = tokenService;
        this.sessionService   = sessionService;
        this.statusAggregator = statusAggregator;
        this.streamingService = streamingService;
        this.contextManager   = contextManager;
    }

    // ── POST /api/chat ──────────────────────────────────────────────────────

    @PostMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String message = body.getOrDefault(REQ_MESSAGE, "").trim();
        if (message.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of(KEY_ERROR, Messages.get(MSG_ERROR_EMPTY_MESSAGE))));
        }

        List<Message> history = sessionService.getOrCreateHistory(session);

        final String jiraToken        = tokenService.getEffectiveJiraToken(session);
        final boolean jiraWriteEnabled = sessionService.isJiraWriteModeEnabled(session);
        final String bitbucketToken   = tokenService.getEffectiveBitbucketToken(session);

        return CompletableFuture.supplyAsync(() -> {
            // Confirmation handlers are null because write tools require SSE streaming.
            contextManager.setupContexts(jiraToken, jiraWriteEnabled, bitbucketToken, null, null);
            try {
                ChatResult result = orchestration.chat(history, message);
                if (result.hasExportData()) {
                    sessionService.setLastQueryResult(session, result.lastToolResult().getQueryResult());
                }
                return ResponseEntity.ok(Map.of(
                        KEY_RESPONSE,   result.response(),
                        KEY_HAS_EXPORT, result.hasExportData()
                ));
            } catch (Exception e) {
                log.error("Chat error", e);
                return ResponseEntity.ok(Map.of(
                        KEY_RESPONSE,   Messages.get(MSG_ERROR_GENERIC, e.getMessage()),
                        KEY_HAS_EXPORT, false
                ));
            } finally {
                contextManager.cleanupAllContexts();
            }
        });
    }

    // ── POST /api/chat/stream (SSE) ──────────────────────────────────────────

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String message = body.getOrDefault(REQ_MESSAGE, "").trim();
        List<Message> history = sessionService.getOrCreateHistory(session);

        String jiraToken = tokenService.getEffectiveJiraToken(session);
        boolean jiraWriteEnabled = sessionService.isJiraWriteModeEnabled(session);
        String bitbucketToken = tokenService.getEffectiveBitbucketToken(session);

        return streamingService.streamChat(
                message, history, session, jiraToken, jiraWriteEnabled, bitbucketToken
        );
    }

    // ── POST /api/chat/cancel ────────────────────────────────────────────────

    @PostMapping("/chat/cancel")
    public ResponseEntity<Map<String, String>> cancelChat(HttpSession session) {
        sessionService.triggerCancellation(session);
        log.info("Chat cancelled via /api/chat/cancel for session {}", session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── POST /api/chat/clear ─────────────────────────────────────────────────

    @PostMapping("/chat/clear")
    public ResponseEntity<Map<String, String>> clear(HttpSession session) {
        sessionService.clearHistory(session);
        sessionService.clearLastQueryResult(session);
        return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
    }

    // ── GET /api/status ──────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        return ResponseEntity.ok(statusAggregator.aggregateStatus(session));
    }
}
