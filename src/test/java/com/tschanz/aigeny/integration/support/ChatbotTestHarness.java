package com.tschanz.aigeny.integration.support;

import com.tschanz.aigeny.chat.ChatResult;
import com.tschanz.aigeny.confirmation.BatchConfirmationService;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.tschanz.aigeny.orchestration.PromptBuilder;
import com.tschanz.aigeny.orchestration.ToolExecutor;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;
import com.tschanz.aigeny.tool.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test harness that assembles a <em>real</em> {@link OrchestrationService} + {@link ToolExecutor}
 * wired with mock tools (database / Jira / Bitbucket, see {@link MockDatabaseTool}, {@link MockJiraTool},
 * {@link MockBitbucketTool}) and a scriptable {@link MockLlmClient}, so a complete conversation
 * ("mock user input" → LLM → tool-call loop → final answer) can be exercised end-to-end without any
 * real external system, network call, or Spring context.
 *
 * <p>Usage:
 * <pre>{@code
 * MockLlmClient llm = new MockLlmClient()
 *         .thenRespondWithToolCall("call-1", "list_tables", "{\"prefix\":\"P_\"}")
 *         .thenRespondWithText("P_PRODUKTE_E");
 * MockDatabaseTool dbTool = MockDatabaseTool.listTables("P_PRODUKTE_E", "P_PRODUKTE_V");
 *
 * ChatbotTestHarness harness = new ChatbotTestHarness(llm, List.of(dbTool));
 * ChatResult result = harness.sendUserMessage("can you tell me the table name containing products");
 * }</pre>
 */
public class ChatbotTestHarness {

    private final MockLlmClient llmClient;
    private final ToolExecutor toolExecutor;
    private final OrchestrationService orchestrationService;
    private final List<Message> history = new ArrayList<>();

    public ChatbotTestHarness(MockLlmClient llmClient, List<Tool> tools) {
        this(llmClient, tools, noopBatchConfirmation());
    }

    public ChatbotTestHarness(MockLlmClient llmClient, List<Tool> tools,
                              BatchConfirmationService batchConfirmationService) {
        this.llmClient = llmClient;
        this.toolExecutor = new ToolExecutor(tools, List.of());
        PromptBuilder promptBuilder;
        try {
            promptBuilder = new PromptBuilder();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize PromptBuilder for test harness", e);
        }
        this.orchestrationService =
                new OrchestrationService(llmClient, toolExecutor, promptBuilder, batchConfirmationService);
    }

    /** Simulates the (mock) user typing {@code message}, driving the real orchestration/tool-call loop. */
    public ChatResult sendUserMessage(String message) throws Exception {
        return orchestrationService.chat(history, message);
    }

    public List<Message> getHistory()             { return history; }
    public MockLlmClient getLlmClient()           { return llmClient; }
    public ToolExecutor getToolExecutor()         { return toolExecutor; }
    public OrchestrationService getOrchestration(){ return orchestrationService; }

    /** Default batch-confirmation stub: reports "unavailable" so the pre-scan step is a no-op. */
    private static BatchConfirmationService noopBatchConfirmation() {
        return new BatchConfirmationService() {
            @Override public boolean isAvailable() { return false; }

            @Override public Map<String, Boolean> requestBatchConfirmation(List<WriteToolCallInfo> writeToolInfos) {
                throw new UnsupportedOperationException(
                        "Batch confirmation not configured in this test harness - "
                        + "pass a custom BatchConfirmationService if the scenario needs it.");
            }

            @Override public void applyPreApprovedDecisions(Map<String, Boolean> decisions) {
                // no-op
            }
        };
    }
}

