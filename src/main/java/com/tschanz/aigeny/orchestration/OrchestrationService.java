package com.tschanz.aigeny.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.*;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    private static final String MSG_UNKNOWN_TOOL      = "orchestration.error.unknown_tool";
    private static final String MSG_TOOL_EXEC_FAILED  = "orchestration.error.tool_execution";
    private static final String MSG_TOOL_LOOP         = "orchestration.error.tool_loop";

    private final LlmClient llmClient;
    private final List<Tool> tools;
    private final String systemPromptTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Spring auto-collects all Tool beans into the list. */
    public OrchestrationService(LlmClient llmClient, List<Tool> tools) throws IOException {
        this.llmClient = llmClient;
        this.tools = tools;
        this.systemPromptTemplate = new ClassPathResource("system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("OrchestrationService initialized with {} tools: {}",
                tools.size(), tools.stream().map(Tool::getName).toList());
    }

    private static final String PERSONA_PRIMER =
            Messages.get(MSG_PERSONA_PRIMER);

    /** Convenience overload without tool-call listener. */
    public ChatResult chat(List<Message> history, String userMessage) throws Exception {
        return chat(history, userMessage, null, null);
    }

    /** Convenience overload with tool-call listener but no intermediate-message listener. */
    public ChatResult chat(List<Message> history, String userMessage,
                           BiConsumer<String, String> onToolCall) throws Exception {
        return chat(history, userMessage, onToolCall, null);
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
        List<ToolDefinition> toolDefs = tools.stream().map(Tool::getDefinition).toList();

        // Prime the persona on the very first message of a new conversation
        if (history.isEmpty()) {
            history.add(Message.assistant(PERSONA_PRIMER));
            log.debug("Injected persona primer as first assistant message");
        }

        history.add(Message.user(userMessage));
        ToolResult lastToolResult = null;

        int iterations = 0;
        while (iterations++ < MAX_TOOL_ITERATIONS) {
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

            for (ToolCall tc : response.getToolCalls()) {
                String toolName = tc.getFunction().getName();
                String toolArgs = tc.getFunction().getArguments();
                log.info("Tool call: {} args: {}", toolName, toolArgs);

                Tool tool = findTool(toolName);
                ToolResult result;
                if (tool == null) {
                    result = new ToolResult(Messages.get(MSG_UNKNOWN_TOOL, toolName));
                    log.warn("Unknown tool: {}", toolName);
                } else {
                    if (onToolCall != null) {
                        onToolCall.accept(toolName, extractCallDescription(toolArgs, toolName));
                    }
                    try {
                        result = tool.execute(toolArgs);
                        if (result.hasQueryResult()) lastToolResult = result;
                    } catch (Exception e) {
                        log.error("Tool execution failed: {}", e.getMessage(), e);
                        result = new ToolResult(Messages.get(MSG_TOOL_EXEC_FAILED, toolName, e.getMessage()));
                    }
                }
                history.add(Message.tool(tc.getId(), toolName, result.getText()));
            }
        }

        return new ChatResult(Messages.get(MSG_TOOL_LOOP), null);
    }

    private Tool findTool(String name) {
        return tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * Extracts the {@code description} field from the tool-call arguments JSON.
     * Falls back to the tool name if the field is absent or the JSON is invalid.
     */
    private String extractCallDescription(String argsJson, String fallback) {
        try {
            JsonNode node = objectMapper.readTree(argsJson);
            JsonNode desc = node.get("description");
            if (desc != null && !desc.isNull() && !desc.asText().isBlank()) {
                return desc.asText();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private String buildSystemPrompt() {
        return systemPromptTemplate;
    }
}
