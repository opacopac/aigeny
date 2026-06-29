package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.tool.ToolResult;
import com.tschanz.aigeny.tool.jira.ConfirmationContext;
import com.tschanz.aigeny.tool.jira.JiraWriteContext;
import com.tschanz.aigeny.tool.jira.JiraWriteExecutor;
import com.tschanz.aigeny.tool.jira.PendingJiraAction;
import com.tschanz.aigeny.orchestration.CurrentToolCallContext;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates user confirmations for write operations (single and batch).
 * 
 * <p>This service handles:
 * <ul>
 *   <li>Single confirmations - blocks until user approves/declines one action</li>
 *   <li>Batch confirmations - shows one dialog for multiple write tools, collects all decisions</li>
 *   <li>Pre-approval checking - uses cached decisions from batch confirmations</li>
 *   <li>Timeout handling - auto-declines on timeout</li>
 *   <li>Action execution - executes Jira actions when confirmed</li>
 * </ul>
 * 
 * <p>Extracted from {@link ChatStreamingService} to improve testability and single responsibility.
 */
@Service
public class ConfirmationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConfirmationOrchestrator.class);

    private static final long CONFIRMATION_TIMEOUT_MIN = 5L;

    // SSE event types
    private static final String EVENT_TYPE_CONFIRMATION_REQUIRED = "confirmation_required";
    private static final String EVENT_TYPE_BATCH_CONFIRMATION    = "batch_confirmation_required";
    private static final String EVENT_TYPE_INTERMEDIATE          = "intermediate";

    // JSON keys
    private static final String KEY_TYPE        = "type";
    private static final String KEY_DESCRIPTION = "description";
    private static final String KEY_TOOL_NAME   = "toolName";
    private static final String KEY_ACTIONS     = "actions";
    private static final String KEY_RESPONSE    = "response";

    // Message keys
    private static final String MSG_JIRA_WRITE_ERROR      = "chat.jira.write_error";
    private static final String MSG_CONFIRMATION_DECLINED = "jira.confirmation.declined";
    private static final String MSG_CONFIRMATION_TIMEOUT  = "jira.confirmation.timeout";

    private final SessionConfirmationService confirmationService;
    private final JiraWriteExecutor jiraWriteExecutor;
    private final ObjectMapper objectMapper;

    public ConfirmationOrchestrator(SessionConfirmationService confirmationService,
                                   JiraWriteExecutor jiraWriteExecutor,
                                   ObjectMapper objectMapper) {
        this.confirmationService = confirmationService;
        this.jiraWriteExecutor   = jiraWriteExecutor;
        this.objectMapper        = objectMapper;
    }

    /**
     * Handles a single confirmation request for a write tool.
     * 
     * <p>Flow:
     * <ol>
     *   <li>Check if decision was pre-approved via batch confirmation</li>
     *   <li>If pre-approved, execute or decline immediately</li>
     *   <li>Otherwise, send SSE confirmation event and block until user responds</li>
     *   <li>Execute action if confirmed, return declined message otherwise</li>
     * </ol>
     * 
     * @param emitter            SSE emitter for sending events
     * @param session            HTTP session for storing confirmation futures
     * @param jiraToken          Jira API token for action execution
     * @param jiraWriteEnabled   whether Jira write operations are enabled
     * @param humanDescription   markdown description shown to user
     * @param action             the pending Jira action to execute if confirmed
     * @return ToolResult with execution result or declined/timeout message
     */
    public ToolResult handleSingleConfirmation(SseEmitter emitter,
                                              HttpSession session,
                                              String jiraToken,
                                              boolean jiraWriteEnabled,
                                              String humanDescription,
                                              PendingJiraAction action) {
        // Check if this write tool's decision was pre-approved via a batch dialog
        String currentToolCallId = CurrentToolCallContext.get();
        if (currentToolCallId != null && ConfirmationContext.hasPreApproved(currentToolCallId)) {
            boolean confirmed = ConfirmationContext.getPreApproved(currentToolCallId);
            log.info("Using pre-approved decision ({}) for tool call {}", 
                    confirmed ? "confirmed" : "declined", currentToolCallId);
            return executeOrDecline(confirmed, emitter, jiraToken, jiraWriteEnabled, action);
        }

        // No pre-approval: show individual confirmation dialog
        sendConfirmationRequired(emitter, humanDescription);

        CompletableFuture<Boolean> future = confirmationService.createConfirmationFuture(session);
        try {
            boolean confirmed = future.get(CONFIRMATION_TIMEOUT_MIN, TimeUnit.MINUTES);
            return executeOrDecline(confirmed, emitter, jiraToken, jiraWriteEnabled, action);
        } catch (TimeoutException e) {
            log.warn("Confirmation timed out for session {}", session.getId());
            return new ToolResult(Messages.get(MSG_CONFIRMATION_TIMEOUT));
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return new ToolResult("Confirmation error: " + e.getMessage());
        }
    }

    /**
     * Handles batch confirmation for multiple write tools in one LLM response.
     * 
     * <p>Sends a single SSE event listing all pending actions, blocks until user
     * submits all decisions, and returns a map of toolCallId → confirmed.
     * 
     * @param emitter         SSE emitter for sending events
     * @param session         HTTP session for storing batch confirmation futures
     * @param writeToolInfos  list of write tool calls requiring confirmation
     * @return map of toolCallId to confirmed (true) or declined (false)
     */
    public Map<String, Boolean> handleBatchConfirmation(SseEmitter emitter,
                                                        HttpSession session,
                                                        List<WriteToolCallInfo> writeToolInfos) {
        sendBatchConfirmationRequired(emitter, writeToolInfos);

        CompletableFuture<Map<String, Boolean>> future = confirmationService.createBatchConfirmationFuture(session);
        try {
            Map<String, Boolean> result = future.get(CONFIRMATION_TIMEOUT_MIN, TimeUnit.MINUTES);
            
            // Handle "confirmAll" shortcut: expand to per-ID decisions
            if (result.containsKey("__confirmAll__")) {
                boolean confirmAll = Boolean.TRUE.equals(result.get("__confirmAll__"));
                log.info("Batch confirmation using confirmAll={} for {} tools", confirmAll, writeToolInfos.size());
                Map<String, Boolean> expanded = new HashMap<>();
                writeToolInfos.forEach(info -> expanded.put(info.toolCallId(), confirmAll));
                return expanded;
            }
            
            return result;
        } catch (TimeoutException e) {
            log.warn("Batch confirmation timed out for session {}", session.getId());
            // All declined on timeout
            Map<String, Boolean> declined = new HashMap<>();
            writeToolInfos.forEach(info -> declined.put(info.toolCallId(), false));
            return declined;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Batch confirmation failed for session {}", session.getId(), e);
            // All declined on error
            Map<String, Boolean> declined = new HashMap<>();
            writeToolInfos.forEach(info -> declined.put(info.toolCallId(), false));
            return declined;
        }
    }

    /**
     * Executes the Jira action if confirmed, or returns a declined message.
     * Shared by individual and batch confirmation paths.
     * 
     * @param confirmed        whether the action was confirmed
     * @param emitter          SSE emitter for sending intermediate messages
     * @param jiraToken        Jira API token
     * @param jiraWriteEnabled whether write operations are enabled
     * @param action           the pending action to execute
     * @return ToolResult with execution result or declined message
     */
    private ToolResult executeOrDecline(boolean confirmed,
                                       SseEmitter emitter,
                                       String jiraToken,
                                       boolean jiraWriteEnabled,
                                       PendingJiraAction action) {
        if (confirmed) {
            JiraWriteContext.set(jiraWriteEnabled);
            try {
                String result = jiraWriteExecutor.execute(action, jiraToken);
                sendIntermediateMessage(emitter, result);
                return new ToolResult(result);
            } catch (Exception e) {
                log.error("Jira action execution failed after confirmation", e);
                String errMsg = Messages.get(MSG_JIRA_WRITE_ERROR, e.getMessage());
                return new ToolResult(errMsg);
            }
        } else {
            return new ToolResult(Messages.get(MSG_CONFIRMATION_DECLINED));
        }
    }

    // ── SSE Event Senders ──────────────────────────────────────────────────

    private void sendConfirmationRequired(SseEmitter emitter, String description) {
        try {
            Map<String, Object> event = Map.of(
                    KEY_TYPE, EVENT_TYPE_CONFIRMATION_REQUIRED,
                    KEY_DESCRIPTION, description
            );
            sendJson(emitter, event);
        } catch (Exception e) {
            log.warn("SSE confirmation_required send failed: {}", e.getMessage());
        }
    }

    private void sendBatchConfirmationRequired(SseEmitter emitter, List<WriteToolCallInfo> writeToolInfos) {
        try {
            List<Map<String, String>> actions = writeToolInfos.stream()
                    .map(info -> Map.of(
                            "toolCallId",       info.toolCallId(),
                            KEY_TOOL_NAME,      info.toolName(),
                            KEY_DESCRIPTION,    info.callDescription()
                    ))
                    .toList();
            Map<String, Object> event = Map.of(
                    KEY_TYPE,    EVENT_TYPE_BATCH_CONFIRMATION,
                    KEY_ACTIONS, actions
            );
            sendJson(emitter, event);
        } catch (Exception e) {
            log.warn("SSE batch_confirmation_required send failed: {}", e.getMessage());
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

    private void sendJson(SseEmitter emitter, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().data(json));
    }
}
