package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.*;
import com.tschanz.aigeny.tools.Tool;
import com.tschanz.aigeny.tools.ToolResult;
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
    private static final int MAX_TOOL_ITERATIONS = 10;

    private final LlmClient llmClient;
    private final List<Tool> tools;
    private final SchemaLoader schemaLoader;
    private final String systemPromptTemplate;

    /** Spring auto-collects all Tool beans into the list. */
    public OrchestrationService(LlmClient llmClient, List<Tool> tools, SchemaLoader schemaLoader) throws IOException {
        this.llmClient = llmClient;
        this.tools = tools;
        this.schemaLoader = schemaLoader;
        this.systemPromptTemplate = new ClassPathResource("system-prompt.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        log.info("OrchestrationService initialized with {} tools: {}",
                tools.size(), tools.stream().map(Tool::getName).toList());
    }

    /**
     * Process a user message within an ongoing conversation history.
     *
     * @param history     previous messages (mutated in place — caller owns this list)
     * @param userMessage the new user input
     * @return ChatResult with the final response text and optional QueryResult for export
     */
    private static final String PERSONA_PRIMER =
            "Da, zdravstvuyte comrade! I am AIgeny, your faithful data assistant, da! " +
            "Vat can I help you with today? Ve can query ze database, search Jira tickets, " +
            "or export results to CSV. Horosho, just ask!";

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
                    result = new ToolResult("ERROR: Unknown tool '" + toolName + "'");
                    log.warn("Unknown tool: {}", toolName);
                } else {
                    try {
                        result = tool.execute(toolArgs);
                        if (result.hasQueryResult()) lastToolResult = result;
                    } catch (Exception e) {
                        log.error("Tool execution failed: {}", e.getMessage(), e);
                        result = new ToolResult("ERROR executing " + toolName + ": " + e.getMessage());
                    }
                }
                history.add(Message.tool(tc.getId(), toolName, result.getText()));
            }
        }

        return new ChatResult(
                "Da, I am sorry comrade — AIgeny got stuck in ze tool loop. Please try again, da?", null);
    }

    private Tool findTool(String name) {
        return tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder(systemPromptTemplate);

        String schema = schemaLoader.getSchema();
        if (schema != null && !schema.isBlank()) {
            log.debug("Injecting DB schema into system prompt ({} chars)", schema.length());
            sb.append("\n\nAVAILABLE DATABASE SCHEMA:\n");
            sb.append(schema);
        } else {
            sb.append("\n\nNO DATABASE SCHEMA AVAILABLE. CRITICAL RULES:\n" +
                    "- Do NOT invent, guess or hallucinate any schema, table names or column names.\n" +
                    "- Tell the user the DB is not configured or the schema could not be loaded.\n" +
                    "- Do NOT pretend to have DB access you do not have.");
        }

        return sb.toString();
    }
}
