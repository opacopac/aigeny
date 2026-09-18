package com.tschanz.aigeny.chat;
import com.tschanz.aigeny.jira.JiraContextProvider;
import com.tschanz.aigeny.bitbucket.BitbucketContextProvider;
import com.tschanz.aigeny.session.SessionCancellationService;
import com.tschanz.aigeny.export.SessionExportService;
import com.tschanz.aigeny.confirmation.ConfirmationOrchestrator;
import com.tschanz.aigeny.confirmation.ExecutionContextManager;

import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.chat.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.orchestration.SelectedDataContext;
import com.tschanz.aigeny.database.DataContextSelectionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles Server-Sent Events (SSE) streaming for chat responses.
 * Manages the lifecycle of SSE emitters and sends real-time updates during chat processing.
 *
 * <p>Write tools that require user confirmation use a synchronous approach:
 * the tool sends a {@code confirmation_required} SSE event with the action description,
 * blocks the orchestration thread waiting for a {@link CompletableFuture} stored in the
 * HTTP session, and resumes once the user calls {@code POST /api/jira/confirm-decision}.
 */
@Service
public class ChatStreamingService {

    private final OrchestrationService orchestration;
    private final SessionCancellationService cancellationService;
    private final SessionExportService exportService;
    private final ConfirmationOrchestrator confirmationOrchestrator;
    private final ExecutionContextManager contextManager;
    private final SseStreamManager sseManager;
    private final DataContextSelectionService dataContextSelectionService;

    public ChatStreamingService(OrchestrationService orchestration,
                                SessionCancellationService cancellationService,
                                SessionExportService exportService,
                                ConfirmationOrchestrator confirmationOrchestrator,
                                ExecutionContextManager contextManager,
                                SseStreamManager sseManager,
                                DataContextSelectionService dataContextSelectionService) {
        this.orchestration            = orchestration;
        this.cancellationService      = cancellationService;
        this.exportService            = exportService;
        this.confirmationOrchestrator = confirmationOrchestrator;
        this.contextManager           = contextManager;
        this.sseManager               = sseManager;
        this.dataContextSelectionService = dataContextSelectionService;
    }

    /**
     * Creates and configures an SSE emitter for streaming chat responses.
     */
    public SseEmitter streamChat(String message,
                                  List<Message> history,
                                  HttpSession session,
                                  String jiraToken,
                                  boolean jiraWriteEnabled,
                                  String bitbucketToken,
                                  SelectedDataContext selectedDataContext) {

        SseEmitter emitter = sseManager.createEmitter();

        if (message == null || message.trim().isEmpty()) {
            sseManager.sendErrorAndComplete(emitter, Messages.get("chat.error.empty_message"));
            return emitter;
        }

        AtomicBoolean cancelFlag = cancellationService.createCancelFlag(session);
        emitter.onCompletion(() -> cancelFlag.set(true));
        emitter.onTimeout(() -> cancelFlag.set(true));
        emitter.onError(t -> cancelFlag.set(true));

        CompletableFuture.runAsync(() -> processChatStream(
                emitter, message, history, session, jiraToken, jiraWriteEnabled, bitbucketToken, cancelFlag, selectedDataContext
        ));

        return emitter;
    }

    /**
     * Processes the chat stream asynchronously.
     */
    private void processChatStream(SseEmitter emitter,
                                    String message,
                                    List<Message> history,
                                    HttpSession session,
                                    String jiraToken,
                                    boolean jiraWriteEnabled,
                                    String bitbucketToken,
                                    AtomicBoolean cancelFlag,
                                    SelectedDataContext selectedDataContext) {

        // Setup all ThreadLocal contexts via ExecutionContextManager
        Map<String, String> tokens = new HashMap<>();
        tokens.put(JiraContextProvider.KEY, jiraToken);
        tokens.put(BitbucketContextProvider.KEY, bitbucketToken);
        contextManager.setupContexts(
                tokens,
                jiraWriteEnabled,
                (humanDescription, action) -> confirmationOrchestrator.handleSingleConfirmation(
                        emitter, session, jiraToken, jiraWriteEnabled, humanDescription, action),
                writeToolInfos -> confirmationOrchestrator.handleBatchConfirmation(emitter, session, writeToolInfos)
        );
        dataContextSelectionService.activate(
                selectedDataContext != null ? selectedDataContext.context() : null,
                selectedDataContext != null ? selectedDataContext.stage() : null);

        try {
            runOrchestrationAndComplete(emitter, message, history, session, cancelFlag, selectedDataContext);
        } finally {
            cleanup(session);
        }
    }


    /**
     * Shared core: runs LLM orchestration and sends the final SSE done event.
     * ThreadLocal contexts must already be set by the caller.
     */
    private void runOrchestrationAndComplete(SseEmitter emitter,
                                             String message,
                                             List<Message> history,
                                             HttpSession session,
                                             AtomicBoolean cancelFlag,
                                             SelectedDataContext selectedDataContext) {
        try {
            ChatResult result = orchestration.chat(
                    history,
                    message,
                    (toolName, description) -> sseManager.sendToolCall(emitter, toolName, description),
                    (intermediateText) -> sseManager.sendIntermediateMessage(emitter, intermediateText),
                    cancelFlag::get,
                    selectedDataContext
            );

            if (result.hasExportData()) {
                exportService.setLastQueryResult(session, result.lastToolResult().getQueryResult());
            }

            sseManager.sendCompletionAndClose(emitter, result);

        } catch (InterruptedException ie) {
            sseManager.handleCancellation(emitter, session);
        } catch (Exception e) {
            sseManager.handleError(emitter, e);
        }
    }

    private void cleanup(HttpSession session) {
        cancellationService.clearCancelFlag(session);
        contextManager.cleanupAllContexts();
        dataContextSelectionService.clear();
    }
}
