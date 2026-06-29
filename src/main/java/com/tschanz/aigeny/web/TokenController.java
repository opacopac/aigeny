package com.tschanz.aigeny.web;

import com.tschanz.aigeny.Messages;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for API token and write-mode management.
 *
 * <p>Owns: POST /api/jira/token, DELETE /api/jira/token,
 * POST /api/jira/write-mode, POST /api/bitbucket/token
 *
 * <p>Separated from {@link ChatController} to give each class a single
 * responsibility (S-1 refactoring).
 */
@RestController
@RequestMapping("/api")
public class TokenController {

    // ── JSON request body keys ───────────────────────────────────────────────
    private static final String REQ_TOKEN = "token";

    // ── JSON response keys ───────────────────────────────────────────────────
    private static final String KEY_STATUS = "status";

    // ── JSON response values ─────────────────────────────────────────────────
    private static final String VAL_OK = "ok";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_STATUS_CLEARED = "chat.status.cleared";

    private final TokenService tokenService;
    private final ChatSessionService sessionService;

    public TokenController(TokenService tokenService, ChatSessionService sessionService) {
        this.tokenService   = tokenService;
        this.sessionService = sessionService;
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

    // ── DELETE /api/jira/token ───────────────────────────────────────────────

    @DeleteMapping("/jira/token")
    public ResponseEntity<Map<String, String>> clearJiraToken(HttpSession session) {
        tokenService.clearUserJiraToken(session);
        return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
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
}

