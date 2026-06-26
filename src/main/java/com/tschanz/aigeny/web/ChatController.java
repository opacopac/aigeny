package com.tschanz.aigeny.web;

import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * REST controller for the chat interface and status endpoints.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    // ── JSON request body keys ───────────────────────────────────────────────
    private static final String REQ_MESSAGE       = "message";
    private static final String REQ_TOKEN         = "token";

    // ── JSON response keys ───────────────────────────────────────────────────
    private static final String KEY_ERROR         = "error";
    private static final String KEY_RESPONSE      = "response";
    private static final String KEY_HAS_EXPORT    = "hasExport";
    private static final String KEY_STATUS        = "status";
    private static final String KEY_TABLES        = "tables";

    // ── JSON response values ─────────────────────────────────────────────────
    private static final String VAL_OK    = "ok";
    private static final String VAL_ERROR = "error";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_ERROR_EMPTY_MESSAGE  = "chat.error.empty_message";
    private static final String MSG_ERROR_GENERIC        = "chat.error.generic";
    private static final String MSG_STATUS_CANCELLED     = "chat.status.cancelled";
    private static final String MSG_STATUS_CLEARED       = "chat.status.cleared";
    private static final String MSG_NO_PENDING           = "chat.jira.no_pending_action";

    private final OrchestrationService orchestration;
    private final SchemaLoader schemaLoader;
    private final ObjectMapper objectMapper;
    private final TokenService tokenService;
    private final ChatSessionService sessionService;
    private final StatusAggregatorService statusAggregator;
    private final ChatStreamingService streamingService;

    public ChatController(OrchestrationService orchestration,
                          SchemaLoader schemaLoader,
                          ObjectMapper objectMapper,
                          TokenService tokenService,
                          ChatSessionService sessionService,
                          StatusAggregatorService statusAggregator,
                          ChatStreamingService streamingService) {
        this.orchestration     = orchestration;
        this.schemaLoader      = schemaLoader;
        this.objectMapper      = objectMapper;
        this.tokenService      = tokenService;
        this.sessionService    = sessionService;
        this.statusAggregator  = statusAggregator;
        this.streamingService  = streamingService;
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

        final String jiraToken       = tokenService.getEffectiveJiraToken(session);
        final boolean jiraWriteEnabled = sessionService.isJiraWriteModeEnabled(session);
        final String bitbucketToken  = tokenService.getEffectiveBitbucketToken(session);

        return CompletableFuture.supplyAsync(() -> {
            JiraTokenContext.set(jiraToken);
            JiraWriteContext.set(jiraWriteEnabled);
            BitbucketTokenContext.set(bitbucketToken);
            PendingJiraActionContext.clear();

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
                JiraTokenContext.clear();
                JiraWriteContext.clear();
                BitbucketTokenContext.clear();
                PendingJiraActionContext.clear();
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

    // ── POST /api/jira/confirm-decision ──────────────────────────────────────

    /**
     * Resolves a pending synchronous Jira confirmation.
     * Called by the frontend when the user clicks "Confirm" or "Decline" in the dialog.
     * The SSE stream remains open; the orchestration thread unblocks and continues.
     *
     * @param body JSON with {@code {"confirmed": true}} or {@code {"confirmed": false}}
     */
    @PostMapping("/jira/confirm-decision")
    public ResponseEntity<Map<String, String>> jiraConfirmDecision(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        boolean confirmed = Boolean.parseBoolean(String.valueOf(body.getOrDefault("confirmed", "false")));
        boolean resolved = sessionService.resolveConfirmation(session, confirmed);
        if (!resolved) {
            log.warn("confirm-decision called but no pending confirmation for session {}", session.getId());
            return ResponseEntity.ok(Map.of(KEY_STATUS, "no_pending"));
        }
        log.info("Jira confirmation {} for session {}", confirmed ? "accepted" : "declined", session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── POST /api/jira/batch-confirm-decision ─────────────────────────────────

    /**
     * Resolves a pending batch Jira confirmation for multiple write tool calls.
     * Called by the frontend when the user confirms or declines multiple actions at once.
     * The SSE stream remains open; the orchestration thread unblocks and continues.
     *
     * @param body JSON with {@code {"decisions": {"toolCallId1": true, "toolCallId2": false}}}
     *             or {@code {"confirmAll": true}} / {@code {"confirmAll": false}} as a shortcut
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/jira/batch-confirm-decision")
    public ResponseEntity<Map<String, String>> jiraBatchConfirmDecision(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Map<String, Boolean> decisions;
        if (body.containsKey("decisions")) {
            decisions = (Map<String, Boolean>) body.get("decisions");
        } else {
            // Shortcut: confirmAll true/false applies to all pending actions
            boolean confirmAll = Boolean.parseBoolean(String.valueOf(body.getOrDefault("confirmAll", "false")));
            // The batch future will be resolved with whatever is provided; an empty map means all declined
            // For confirmAll we pass a sentinel handled by the session service, but since we don't know
            // the tool call IDs here we resolve with a special entry. The batch handler in
            // ChatStreamingService already stores the IDs, so we re-use resolveBatchConfirmation
            // with a single-entry map that ChatStreamingService interprets as "apply to all".
            // For simplicity, we require the frontend to send per-ID decisions or use confirmAll
            // with the tool call IDs embedded.
            decisions = Map.of("__confirmAll__", confirmAll);
        }
        boolean resolved = sessionService.resolveBatchConfirmation(session, decisions);
        if (!resolved) {
            log.warn("batch-confirm-decision called but no pending batch future for session {}", session.getId());
            return ResponseEntity.ok(Map.of(KEY_STATUS, "no_pending"));
        }
        log.info("Batch Jira confirmation resolved ({} decisions) for session {}", decisions.size(), session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
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
        return ResponseEntity.ok(Map.of("status", Messages.get(MSG_STATUS_CLEARED)));
    }

    // ── POST /api/schema/reload ──────────────────────────────────────────────

    @PostMapping("/schema/reload")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> reloadSchema() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                schemaLoader.reload();
                return ResponseEntity.ok(Map.of(
                        KEY_STATUS, VAL_OK,
                        KEY_TABLES, schemaLoader.getTableCount()
                ));
            } catch (Exception e) {
                log.error("Schema reload failed", e);
                return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_ERROR, KEY_ERROR, e.getMessage()));
            }
        });
    }

    // ── GET /api/status ──────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        return ResponseEntity.ok(statusAggregator.aggregateStatus(session));
    }

    // ── POST /api/jira/token ─────────────────────────────────────────────────

    @PostMapping("/jira/token")
    public ResponseEntity<Map<String, String>> setJiraToken(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String token = body.getOrDefault(REQ_TOKEN, "").strip();
        tokenService.setUserJiraToken(session, token);
        if (token.isEmpty()) {
            return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
        }
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── POST /api/jira/write-mode ────────────────────────────────────────────

    @PostMapping("/jira/write-mode")
    public ResponseEntity<Map<String, String>> setJiraWriteMode(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "false")));
        sessionService.setJiraWriteMode(session, enabled);
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── DELETE /api/jira/token ───────────────────────────────────────────────

    @DeleteMapping("/jira/token")
    public ResponseEntity<Map<String, String>> clearJiraToken(HttpSession session) {
        tokenService.clearUserJiraToken(session);
        return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
    }

    // ── POST /api/bitbucket/token ────────────────────────────────────────────

    @PostMapping("/bitbucket/token")
    public ResponseEntity<Map<String, String>> setBitbucketToken(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String token = body.getOrDefault(REQ_TOKEN, "").strip();
        tokenService.setUserBitbucketToken(session, token);
        if (token.isEmpty()) {
            return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
        }
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Expose last query result to ExportController within the same session. */
    public static QueryResult getLastResult(HttpSession session) {
        ChatSessionService service = new ChatSessionService();
        return service.getLastQueryResult(session);
    }
}
