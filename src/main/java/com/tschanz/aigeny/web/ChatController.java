package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.tools.QueryResult;
import com.tschanz.aigeny.tools.ToolResult;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private static final String SESSION_HISTORY   = "chatHistory";
    private static final String SESSION_RESULT    = "lastQueryResult";

    private final OrchestrationService orchestration;
    private final SchemaLoader schemaLoader;
    private final AigenyProperties props;

    public ChatController(OrchestrationService orchestration,
                          SchemaLoader schemaLoader,
                          AigenyProperties props) {
        this.orchestration = orchestration;
        this.schemaLoader  = schemaLoader;
        this.props         = props;
    }

    // ── POST /api/chat ──────────────────────────────────────────────────────

    @PostMapping("/chat")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> chat(
            @RequestBody Map<String, String> body,
            HttpSession session) {

        String message = body.getOrDefault("message", "").trim();
        if (message.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of("error", "Empty message")));
        }

        List<Message> history = getOrCreateHistory(session);

        return CompletableFuture.supplyAsync(() -> {
            try {
                ChatResult result = orchestration.chat(history, message);

                // Persist last query result in session for export
                if (result.hasExportData()) {
                    session.setAttribute(SESSION_RESULT, result.lastToolResult().getQueryResult());
                }

                return ResponseEntity.ok(Map.of(
                        "response",   result.response(),
                        "hasExport",  result.hasExportData()
                ));
            } catch (Exception e) {
                log.error("Chat error", e);
                return ResponseEntity.ok(Map.of(
                        "response", "Nyet! Something vent wrong, comrade: " + e.getMessage(),
                        "hasExport", false
                ));
            }
        });
    }

    // ── POST /api/chat/clear ─────────────────────────────────────────────────

    @PostMapping("/chat/clear")
    public ResponseEntity<Map<String, String>> clear(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
        session.removeAttribute(SESSION_RESULT);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    // ── POST /api/schema/reload ──────────────────────────────────────────────

    @PostMapping("/schema/reload")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> reloadSchema() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String schema = schemaLoader.reload();
                return ResponseEntity.ok(Map.of(
                        "status",     "ok",
                        "tables",     schemaLoader.getTableCount(),
                        "schemaSize", schema.length()
                ));
            } catch (Exception e) {
                log.error("Schema reload failed", e);
                return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
            }
        });
    }

    // ── GET /api/status ──────────────────────────────────────────────────────

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(HttpSession session) {
        QueryResult lastResult = (QueryResult) session.getAttribute(SESSION_RESULT);
        return ResponseEntity.ok(Map.of(
                "llmProvider",  props.getLlm().getProvider(),
                "llmModel",     props.getLlm().getModel(),
                "dbConfigured", props.isDbConfigured(),
                "jiraConfigured", props.isJiraConfigured(),
                "schemaTables", schemaLoader.getTableCount(),
                "hasExport",    lastResult != null && !lastResult.isEmpty()
        ));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Message> getOrCreateHistory(HttpSession session) {
        List<Message> history = (List<Message>) session.getAttribute(SESSION_HISTORY);
        if (history == null) {
            history = new ArrayList<>();
            session.setAttribute(SESSION_HISTORY, history);
        }
        return history;
    }

    /** Expose last query result to ExportController within the same session. */
    public static QueryResult getLastResult(HttpSession session) {
        return (QueryResult) session.getAttribute(SESSION_RESULT);
    }
}

