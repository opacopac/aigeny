package com.tschanz.aigeny.orchestration;

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

    /** Spring auto-collects all Tool beans into the list. */
    public OrchestrationService(LlmClient llmClient, List<Tool> tools) throws IOException {
        this.llmClient = llmClient;
        this.tools = tools;
        this.systemPromptTemplate = new ClassPathResource("system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("OrchestrationService initialized with {} tools: {}",
                tools.size(), tools.stream().map(Tool::getName).toList());
    }

    /**
     * Process a user message within an ongoing conversation history.
     *
     * @param history     previous messages (mutated in place - caller owns this list)
     * @param userMessage the new user input
     * @return ChatResult with the final response text and optional QueryResult for export
     */
    private static final String PERSONA_PRIMER =
            Messages.get(MSG_PERSONA_PRIMER);

    // ...existing code...

    public ChatResult chat(List<Message> history, String userMessage) throws Exception {
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

    private String buildSystemPrompt() {
        return systemPromptTemplate;
    }
}
