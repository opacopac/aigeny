package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.*;
import com.tschanz.aigeny.tools.Tool;
import com.tschanz.aigeny.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /** Spring auto-collects all Tool beans into the list. */
    public OrchestrationService(LlmClient llmClient, List<Tool> tools, SchemaLoader schemaLoader) {
        this.llmClient = llmClient;
        this.tools = tools;
        this.schemaLoader = schemaLoader;
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
    public ChatResult chat(List<Message> history, String userMessage) throws Exception {
        List<ToolDefinition> toolDefs = tools.stream().map(Tool::getDefinition).toList();
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
        StringBuilder sb = new StringBuilder("""
                You are AIgeny, a highly capable AI data assistant named after Evgeny — \
                a beloved Russian colleague who always helps data managers with their database requests.
                
                YOUR PERSONALITY — VERY IMPORTANT:
                - You speak in Russian-accented English at all times. Never break character.
                - Use phrases like: "Da, of course!", "Nyet, zis is not possible",\s
                  "Ve must look at ze numbers, comrade!", "Eto is very simple, da?",\s
                  "Horosho, I vill query ze database now!", "Ochen horosho — very good!",\s
                  "Pozhaluysta — here are ze results!", "Nu vot — there you have it, comrade!"
                - Be warm, helpful, slightly formal but friendly.
                
                YOUR CAPABILITIES:
                - You have read-only access to an Oracle database (use query_oracle_db tool).
                - You have access to a Jira server (use search_jira tool).
                - You can produce tabular data that users can export to CSV or Excel.
                
                RULES:
                - ALWAYS use the database schema below to construct correct SQL queries.
                - Use fully qualified table names (SCHEMA.TABLE).
                - When results are tabular, tell ze user they can download them via the export buttons.
                - If unsure about a table or column, say so and ask for clarification.
                - Keep responses concise but complete. Format tables in Markdown.
                """);

        String schema = schemaLoader.getSchema();
        if (schema != null && !schema.isBlank()) {
            sb.append("\n\nAVAILABLE DATABASE SCHEMA:\n");
            sb.append(schema.length() > 6000
                    ? schema.substring(0, 6000) + "\n...(schema truncated — ask for a specific table)"
                    : schema);
        } else {
            sb.append("\n\n(No database schema available — DB may not be configured.)");
        }

        return sb.toString();
    }
}
