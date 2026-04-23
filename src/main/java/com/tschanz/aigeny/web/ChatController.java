package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.tools.JiraTokenContext;
import com.tschanz.aigeny.tools.JiraWriteExecutor;
import com.tschanz.aigeny.tools.PendingJiraAction;
import com.tschanz.aigeny.tools.PendingJiraActionContext;
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
    private static final String SESSION_HISTORY        = "chatHistory";
    private static final String SESSION_RESULT         = "lastQueryResult";
    private static final String SESSION_JIRA_TOKEN     = "jiraToken";
    private static final String SESSION_PENDING_ACTION = "pendingJiraAction";

    private final OrchestrationService orchestration;
    private final SchemaLoader schemaLoader;
    private final AigenyProperties props;
    private final JiraWriteExecutor jiraWriteExecutor;

    public ChatController(OrchestrationService orchestration,
                          SchemaLoader schemaLoader,
                          AigenyProperties props,
                          JiraWriteExecutor jiraWriteExecutor) {
        this.orchestration     = orchestration;
        this.schemaLoader      = schemaLoader;
        this.props             = props;
        this.jiraWriteExecutor = jiraWriteExecutor;
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

        // Read token in the HTTP thread (RequestContextHolder is available here)
        // then pass it into the async lambda via ThreadLocal
        final String jiraToken = getEffectiveJiraToken(session, props);

        return CompletableFuture.supplyAsync(() -> {
            JiraTokenContext.set(jiraToken);
            PendingJiraActionContext.clear();
            try {
                ChatResult result = orchestration.chat(history, message);

                // Persist last query result in session for export
                if (result.hasExportData()) {
                    session.setAttribute(SESSION_RESULT, result.lastToolResult().getQueryResult());
                }

                // Check for a pending Jira write action queued by a tool
                PendingJiraAction pending = PendingJiraActionContext.get();
                if (pending != null) {
                    session.setAttribute(SESSION_PENDING_ACTION, pending);
                    return ResponseEntity.ok(Map.of(
                            "response",       result.response(),
                            "hasExport",      result.hasExportData(),
                            "pendingAction",  Map.of(
                                    "description", pending.getHumanDescription(),
                                    "issueKey",    pending.getIssueKey()
                            )
                    ));
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
            } finally {
                JiraTokenContext.clear();
                PendingJiraActionContext.clear();
            }
        });
    }

    // ── POST /api/jira/confirm ───────────────────────────────────────────────

    @PostMapping("/jira/confirm")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> confirmJiraAction(HttpSession session) {
        PendingJiraAction pending = (PendingJiraAction) session.getAttribute(SESSION_PENDING_ACTION);
        if (pending == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(Map.of("result", "Njet! Keine ausstehende Aktion gefunden, Towarischtsch.")));
        }
        session.removeAttribute(SESSION_PENDING_ACTION);
        final String token = getEffectiveJiraToken(session, props);
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(Map.of("result", "Njet! Kein Jira-Token konfiguriert.")));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                String result = jiraWriteExecutor.execute(pending, token);
                return ResponseEntity.ok(Map.<String, Object>of("result", result));
            } catch (Exception e) {
                log.error("Jira write failed", e);
                return ResponseEntity.ok(Map.<String, Object>of(
                        "result", "Njet! Fehler bei Jira-Aktion: " + e.getMessage()));
            }
        });
    }

    // ── POST /api/jira/cancel ────────────────────────────────────────────────

    @PostMapping("/jira/cancel")
    public ResponseEntity<Map<String, String>> cancelJiraAction(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_ACTION);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
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
                schemaLoader.reload();
                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "tables", schemaLoader.getTableCount()
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
        String userJiraToken = (String) session.getAttribute(SESSION_JIRA_TOKEN);
        boolean jiraTokenAvailable = (userJiraToken != null && !userJiraToken.isBlank())
                || props.isJiraConfigured();
        boolean jiraBaseUrlConfigured = props.getJira().getBaseUrl() != null
                && !props.getJira().getBaseUrl().isBlank();
        return ResponseEntity.ok(Map.of(
                "llmProvider",          props.getLlm().getProvider(),
                "llmModel",             props.getLlm().getModel(),
                "dbConfigured",         props.isDbConfigured(),
                "jiraConfigured",       jiraTokenAvailable,
                "jiraBaseUrlConfigured",jiraBaseUrlConfigured,
                "schemaTables",         schemaLoader.getTableCount(),
                "hasExport",            lastResult != null && !lastResult.isEmpty()
        ));
    }

    // ── POST /api/jira/token ─────────────────────────────────────────────────

    @PostMapping("/jira/token")
    public ResponseEntity<Map<String, String>> setJiraToken(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String token = body.getOrDefault("token", "").strip();
        if (token.isEmpty()) {
            session.removeAttribute(SESSION_JIRA_TOKEN);
            return ResponseEntity.ok(Map.of("status", "cleared"));
        }
        session.setAttribute(SESSION_JIRA_TOKEN, token);
        log.info("User Jira token set for session {}", session.getId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // ── DELETE /api/jira/token ───────────────────────────────────────────────

    @DeleteMapping("/jira/token")
    public ResponseEntity<Map<String, String>> clearJiraToken(HttpSession session) {
        session.removeAttribute(SESSION_JIRA_TOKEN);
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }

    /** Returns the effective Jira token for the current session (user override or config fallback). */
    public static String getEffectiveJiraToken(HttpSession session, AigenyProperties props) {
        String userToken = session != null ? (String) session.getAttribute(SESSION_JIRA_TOKEN) : null;
        if (userToken != null && !userToken.isBlank()) return userToken;
        return props.getJira().getToken();
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

