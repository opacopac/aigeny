package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteExecutor;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.Messages;
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

    // ── Session attribute keys ───────────────────────────────────────────────
    private static final String SESSION_HISTORY        = "chatHistory";
    private static final String SESSION_RESULT         = "lastQueryResult";
    private static final String SESSION_JIRA_TOKEN     = "jiraToken";
    private static final String SESSION_PENDING_ACTION = "pendingJiraAction";
    private static final String SESSION_JIRA_WRITE     = "jiraWriteEnabled";

    // ── JSON request body keys ───────────────────────────────────────────────
    private static final String REQ_MESSAGE       = "message";
    private static final String REQ_TOKEN         = "token";

    // ── JSON response keys ───────────────────────────────────────────────────
    private static final String KEY_ERROR                    = "error";
    private static final String KEY_RESPONSE                 = "response";
    private static final String KEY_HAS_EXPORT               = "hasExport";
    private static final String KEY_PENDING_ACTION           = "pendingAction";
    private static final String KEY_DESCRIPTION              = "description";
    private static final String KEY_ISSUE_KEY                = "issueKey";
    private static final String KEY_RESULT                   = "result";
    private static final String KEY_STATUS                   = "status";
    private static final String KEY_TABLES                   = "tables";
    private static final String KEY_LLM_PROVIDER             = "llmProvider";
    private static final String KEY_LLM_MODEL                = "llmModel";
    private static final String KEY_DB_CONFIGURED            = "dbConfigured";
    private static final String KEY_JIRA_CONFIGURED          = "jiraConfigured";
    private static final String KEY_JIRA_BASEURL_CONFIGURED  = "jiraBaseUrlConfigured";
    private static final String KEY_JIRA_WRITE_ENABLED       = "jiraWriteEnabled";
    private static final String KEY_DB_USERNAME               = "dbUsername";
    private static final String KEY_SCHEMA_TABLES            = "schemaTables";

    // ── JSON response values ─────────────────────────────────────────────────
    private static final String VAL_OK    = "ok";
    private static final String VAL_ERROR = "error";

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_ERROR_EMPTY_MESSAGE = "chat.error.empty_message";
    private static final String MSG_ERROR_GENERIC       = "chat.error.generic";
    private static final String MSG_JIRA_NO_PENDING     = "chat.jira.no_pending_action";
    private static final String MSG_JIRA_NO_TOKEN       = "chat.jira.no_token";
    private static final String MSG_JIRA_WRITE_ERROR    = "chat.jira.write_error";
    private static final String MSG_STATUS_CANCELLED    = "chat.status.cancelled";
    private static final String MSG_STATUS_CLEARED      = "chat.status.cleared";

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

        String message = body.getOrDefault(REQ_MESSAGE, "").trim();
        if (message.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body(Map.of(KEY_ERROR, Messages.get(MSG_ERROR_EMPTY_MESSAGE))));
        }

        List<Message> history = getOrCreateHistory(session);

        // Read token in the HTTP thread (RequestContextHolder is available here)
        // then pass it into the async lambda via ThreadLocal
        final String jiraToken = getEffectiveJiraToken(session, props);
        final boolean jiraWriteEnabled = Boolean.TRUE.equals(session.getAttribute(SESSION_JIRA_WRITE));

        return CompletableFuture.supplyAsync(() -> {
            JiraTokenContext.set(jiraToken);
            JiraWriteContext.set(jiraWriteEnabled);
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
                            KEY_RESPONSE,      result.response(),
                            KEY_HAS_EXPORT,    result.hasExportData(),
                            KEY_PENDING_ACTION, Map.of(
                                    KEY_DESCRIPTION, pending.getHumanDescription(),
                                    KEY_ISSUE_KEY,   pending.getIssueKey()
                            )
                    ));
                }

                return ResponseEntity.ok(Map.of(
                        KEY_RESPONSE,  result.response(),
                        KEY_HAS_EXPORT, result.hasExportData()
                ));
            } catch (Exception e) {
                log.error("Chat error", e);
                return ResponseEntity.ok(Map.of(
                        KEY_RESPONSE,  Messages.get(MSG_ERROR_GENERIC, e.getMessage()),
                        KEY_HAS_EXPORT, false
                ));
            } finally {
                JiraTokenContext.clear();
                JiraWriteContext.clear();
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
                    ResponseEntity.ok(Map.of(KEY_RESULT, Messages.get(MSG_JIRA_NO_PENDING))));
        }
        session.removeAttribute(SESSION_PENDING_ACTION);
        final String token = getEffectiveJiraToken(session, props);
        if (token == null || token.isBlank()) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok(Map.of(KEY_RESULT, Messages.get(MSG_JIRA_NO_TOKEN))));
        }
        final boolean writeEnabled = Boolean.TRUE.equals(session.getAttribute(SESSION_JIRA_WRITE));
        return CompletableFuture.supplyAsync(() -> {
            JiraWriteContext.set(writeEnabled);
            try {
                String result = jiraWriteExecutor.execute(pending, token);
                return ResponseEntity.ok(Map.<String, Object>of(KEY_RESULT, result));
            } catch (Exception e) {
                log.error("Jira write failed", e);
                return ResponseEntity.ok(Map.<String, Object>of(
                        KEY_RESULT, Messages.get(MSG_JIRA_WRITE_ERROR, e.getMessage())));
            } finally {
                JiraWriteContext.clear();
            }
        });
    }

    // ── POST /api/jira/cancel ────────────────────────────────────────────────

    @PostMapping("/jira/cancel")
    public ResponseEntity<Map<String, String>> cancelJiraAction(HttpSession session) {
        session.removeAttribute(SESSION_PENDING_ACTION);
        return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CANCELLED)));
    }

    // ── POST /api/chat/clear ─────────────────────────────────────────────────

    @PostMapping("/chat/clear")
    public ResponseEntity<Map<String, String>> clear(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
        session.removeAttribute(SESSION_RESULT);
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
        QueryResult lastResult = (QueryResult) session.getAttribute(SESSION_RESULT);
        String userJiraToken = (String) session.getAttribute(SESSION_JIRA_TOKEN);
        boolean jiraTokenAvailable = (userJiraToken != null && !userJiraToken.isBlank())
                || props.isJiraConfigured();
        boolean jiraBaseUrlConfigured = props.getJira().getBaseUrl() != null
                && !props.getJira().getBaseUrl().isBlank();
        boolean jiraWriteEnabled = Boolean.TRUE.equals(session.getAttribute(SESSION_JIRA_WRITE));
        return ResponseEntity.ok(Map.of(
                KEY_LLM_PROVIDER,            props.getLlm().getProvider(),
                KEY_LLM_MODEL,               props.getLlm().getModel(),
                KEY_DB_CONFIGURED,           props.isDbConfigured(),
                KEY_DB_USERNAME,             props.getDb().getUsername(),
                KEY_JIRA_CONFIGURED,         jiraTokenAvailable,
                KEY_JIRA_BASEURL_CONFIGURED, jiraBaseUrlConfigured,
                KEY_JIRA_WRITE_ENABLED,      jiraWriteEnabled,
                KEY_SCHEMA_TABLES,           schemaLoader.getTableCount(),
                KEY_HAS_EXPORT,              lastResult != null && !lastResult.isEmpty()
        ));
    }

    // ── POST /api/jira/token ─────────────────────────────────────────────────

    @PostMapping("/jira/token")
    public ResponseEntity<Map<String, String>> setJiraToken(
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String token = body.getOrDefault(REQ_TOKEN, "").strip();
        if (token.isEmpty()) {
            session.removeAttribute(SESSION_JIRA_TOKEN);
            return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
        }
        session.setAttribute(SESSION_JIRA_TOKEN, token);
        log.info("User Jira token set for session {}", session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── POST /api/jira/write-mode ────────────────────────────────────────────

    @PostMapping("/jira/write-mode")
    public ResponseEntity<Map<String, String>> setJiraWriteMode(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        boolean enabled = Boolean.parseBoolean(String.valueOf(body.getOrDefault("enabled", "false")));
        session.setAttribute(SESSION_JIRA_WRITE, enabled);
        log.info("Jira write mode {} for session {}", enabled ? "enabled" : "disabled", session.getId());
        return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_OK));
    }

    // ── DELETE /api/jira/token ───────────────────────────────────────────────

    @DeleteMapping("/jira/token")
    public ResponseEntity<Map<String, String>> clearJiraToken(HttpSession session) {
        session.removeAttribute(SESSION_JIRA_TOKEN);
        return ResponseEntity.ok(Map.of(KEY_STATUS, Messages.get(MSG_STATUS_CLEARED)));
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

