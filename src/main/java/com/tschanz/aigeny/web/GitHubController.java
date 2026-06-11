package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.GitHubCopilotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints that drive the GitHub Copilot device-flow Connect dialog.
 *
 *   POST /api/github/connect      → start device flow, return user_code + URL
 *   GET  /api/github/status       → connected? pairing? login?
 *   POST /api/github/disconnect   → revoke local pairing
 *   GET  /api/github/models       → list models (also logs them)
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private static final Logger log = LoggerFactory.getLogger(GitHubController.class);

    private final GitHubCopilotService github;

    public GitHubController(GitHubCopilotService github) {
        this.github = github;
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        try {
            GitHubCopilotService.DeviceFlowStart start = github.startDeviceFlow();
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("userCode",        start.userCode());
            resp.put("verificationUri", start.verificationUri());
            resp.put("expiresIn",       start.expiresIn());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("GitHub connect failed", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(github.getStatus());
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect() {
        github.disconnect();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> models() {
        try {
            List<String> models = github.listModels();
            log.info("GitHub Copilot models (on request): {}", models);
            return ResponseEntity.ok(Map.of("models", models));
        } catch (Exception e) {
            log.warn("Listing models failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

