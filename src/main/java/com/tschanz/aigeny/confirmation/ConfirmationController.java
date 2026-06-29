package com.tschanz.aigeny.confirmation;

import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Jira write-action confirmation endpoints.
 *
 * <p>Owns: POST /api/jira/confirm-decision, POST /api/jira/batch-confirm-decision
 *
 * <p>Separated from {@link ChatController} to give each class a single
 * responsibility (S-1 refactoring).
 */
@RestController
@RequestMapping("/api")
public class ConfirmationController {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationController.class);

    private static final String KEY_STATUS    = "status";
    private static final String VAL_OK        = "ok";
    private static final String VAL_NO_PENDING = "no_pending";

    private final SessionConfirmationService confirmationService;

    public ConfirmationController(SessionConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
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
        boolean resolved = confirmationService.resolveConfirmation(session, confirmed);
        if (!resolved) {
            log.warn("confirm-decision called but no pending confirmation for session {}", session.getId());
            return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_NO_PENDING));
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
            // Shortcut: confirmAll=true/false applies to all pending actions.
            // The ConfirmationOrchestrator recognises the "__confirmAll__" sentinel
            // and expands it to per-tool-call decisions.
            boolean confirmAll = Boolean.parseBoolean(String.valueOf(body.getOrDefault("confirmAll", "false")));
            decisions = Map.of("__confirmAll__", confirmAll);
        }
        boolean resolved = confirmationService.resolveBatchConfirmation(session, decisions);
        if (!resolved) {
            log.warn("batch-confirm-decision called but no pending batch future for session {}", session.getId());
            return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_NO_PENDING));
        }
        log.info("Batch Jira confirmation resolved ({} decisions) for session {}", decisions.size(), session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }
}

