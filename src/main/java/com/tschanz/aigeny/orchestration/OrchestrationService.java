package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.*;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Core orchestration: builds the system prompt, manages the agentic tool-call loop,
 * returns a ChatResult containing the response text and any tabular data.
 */
@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);
    private static final int MAX_TOOL_ITERATIONS = 50;

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_PERSONA_PRIMER    = "orchestration.persona_primer";
    private static final String MSG_TOOL_LOOP         = "orchestration.error.tool_loop";

    private final LlmClient llmClient;
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final BatchConfirmationService batchConfirmationService;

    public OrchestrationService(LlmClient llmClient,
                                ToolExecutor toolExecutor,
                                PromptBuilder promptBuilder,
                                BatchConfirmationService batchConfirmationService) {
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
        this.promptBuilder = promptBuilder;
        this.batchConfirmationService = batchConfirmationService;
        log.info("OrchestrationService initialized with {} tools",
                toolExecutor.getToolCount());
    }

    private static final String PERSONA_PRIMER =
            Messages.get(MSG_PERSONA_PRIMER);

    /** Convenience overload without tool-call listener. */
    public ChatResult chat(List<Message> history, String userMessage) throws Exception {
        return chat(history, userMessage, null, null, null);
    }

    /** Convenience overload with tool-call listener but no intermediate-message listener. */
    public ChatResult chat(List<Message> history, String userMessage,
                           BiConsumer<String, String> onToolCall) throws Exception {
        return chat(history, userMessage, onToolCall, null, null);
    }

    /**
     * Process a user message.
     * <ul>
     *   <li>{@code onToolCall} – called with (toolName, description) before each tool execution (may be {@code null})</li>
     *   <li>{@code onIntermediateMessage} – called with the text when the LLM returns text alongside tool calls (may be {@code null})</li>
     * </ul>
     */
    public ChatResult chat(List<Message> history, String userMessage,
                           BiConsumer<String, String> onToolCall,
                           java.util.function.Consumer<String> onIntermediateMessage) throws Exception {
        return chat(history, userMessage, onToolCall, onIntermediateMessage, null);
    }

    /**
     * Full overload with cancellation support.
     * {@code isCancelled} is polled at the start of each loop iteration; when it returns {@code true}
     * an {@link InterruptedException} is thrown to abort the loop.
     */
    public ChatResult chat(List<Message> history, String userMessage,
                           BiConsumer<String, String> onToolCall,
                           java.util.function.Consumer<String> onIntermediateMessage,
                           java.util.function.Supplier<Boolean> isCancelled) throws Exception {
        List<ToolDefinition> toolDefs = toolExecutor.getTools().stream()
                .map(Tool::getDefinition)
                .toList();

        // Prime the persona on the very first message of a new conversation
        if (history.isEmpty()) {
            history.add(Message.assistant(PERSONA_PRIMER));
            log.debug("Injected persona primer as first assistant message");
        }

        String now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy HH:mm:ss z"));
        String stampedMessage = "[Current date & time: " + now + "]\n\n" + userMessage;
        history.add(Message.user(stampedMessage));

        ToolResult lastToolResult = null;

        int iterations = 0;
        while (iterations++ < MAX_TOOL_ITERATIONS) {
            if (isCancelled != null && isCancelled.get()) {
                log.info("Chat loop cancelled by user after {} iteration(s)", iterations - 1);
                throw new InterruptedException("Cancelled by user");
            }
            List<Message> fullMessages = new ArrayList<>();
            fullMessages.add(Message.system(buildSystemPrompt()));
            fullMessages.addAll(history);

            log.info("LLM call #{} ({} messages)", iterations, fullMessages.size());
            ChatResponse response = llmClient.chat(fullMessages, toolDefs);

            if (!response.hasToolCalls()) {
                String content = response.getContent();
                history.add(Message.assistant(content));
                log.info("Final response ({} chars)", content.length());
                return new ChatResult(content, lastToolResult);
            }

            // Emit intermediate text (LLM "thinking out loud" before calling tools)
            if (response.hasIntermediateText()) {
                String intermediate = response.getIntermediateText();
                log.info("Intermediate message ({} chars)", intermediate.length());
                if (onIntermediateMessage != null) {
                    onIntermediateMessage.accept(intermediate);
                }
            }

            Message assistantMsg = Message.assistantWithToolCalls(response.getToolCalls());
            history.add(assistantMsg);

            // Pre-scan for multiple write tool calls in this response: if found,
            // request one batch confirmation dialog instead of one per write tool.
            preScanAndBatchConfirm(response.getToolCalls());

            for (ToolCall tc : response.getToolCalls()) {
                ToolResult result = toolExecutor.executeToolCall(tc, onToolCall);
                if (result.hasQueryResult()) lastToolResult = result;
                history.add(Message.tool(tc.getId(), tc.getFunction().getName(), result.getText()));
            }
        }

        return new ChatResult(Messages.get(MSG_TOOL_LOOP), null);
    }

    private String buildSystemPrompt() {
        return promptBuilder.buildSystemPrompt();
    }

    /**
     * Pre-scans tool calls from a single LLM response for write actions.
     * If two or more write tool calls are found and a batch confirmation handler is registered,
     * requests one combined confirmation dialog and stores the decisions so individual write
     * tools skip their own blocking confirmation flow.
     */
    private void preScanAndBatchConfirm(List<ToolCall> toolCalls) {
        if (!batchConfirmationService.isAvailable()) return;

        List<WriteToolCallInfo> writeCalls = toolCalls.stream()
                .filter(tc -> toolExecutor.findTool(tc.getFunction().getName())
                        .map(Tool::requiresConfirmation)
                        .orElse(false))
                .map(tc -> {
                    String desc = toolExecutor.findTool(tc.getFunction().getName())
                            .map(t -> t.getCallDescription(tc.getFunction().getArguments()))
                            .orElse(tc.getFunction().getName());
                    return new WriteToolCallInfo(tc.getId(), tc.getFunction().getName(), desc);
                })
                .toList();

        if (writeCalls.size() <= 1) return;

        log.info("Batch confirmation: {} write tool calls detected, requesting combined dialog", writeCalls.size());
        Map<String, Boolean> decisions = batchConfirmationService.requestBatchConfirmation(writeCalls);
        batchConfirmationService.applyPreApprovedDecisions(decisions);
    }
}
