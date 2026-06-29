package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.ChatResponse;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm.model.ToolCall;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrchestrationService}, focusing on the
 * {@code preScanAndBatchConfirm} logic which previously depended on static
 * ThreadLocal classes.  The injected {@link BatchConfirmationService} mock
 * makes all interactions fully observable and controllable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestrationService")
class OrchestrationServiceTest {

    @Mock private LlmClient llmClient;
    @Mock private ToolExecutor toolExecutor;
    @Mock private PromptBuilder promptBuilder;
    @Mock private BatchConfirmationService batchConfirmationService;

    @Mock private Tool writeTool1;
    @Mock private Tool writeTool2;

    private OrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new OrchestrationService(llmClient, toolExecutor, promptBuilder, batchConfirmationService);
        // Use lenient so tests that cancel immediately don't fail with UnnecessaryStubbingException
        lenient().when(promptBuilder.buildSystemPrompt()).thenReturn("System prompt");
        lenient().when(toolExecutor.getTools()).thenReturn(List.of());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ToolCall writeToolCall(String id, String name) {
        return new ToolCall(id, new ToolCall.FunctionCall(name, "{}"));
    }

    private ToolCall readToolCall(String id, String name) {
        return new ToolCall(id, new ToolCall.FunctionCall(name, "{}"));
    }

    /** Stubs toolExecutor to recognise {@code toolName} as a write-tool requiring confirmation. */
    private void stubWriteTool(Tool mock, String toolName, String description) {
        when(toolExecutor.findTool(toolName)).thenReturn(Optional.of(mock));
        when(mock.requiresConfirmation()).thenReturn(true);
        when(mock.getCallDescription(anyString())).thenReturn(description);
    }

    /** Stubs toolExecutor to recognise {@code toolName} as a read-tool (no confirmation). */
    private void stubReadTool(Tool mock, String toolName) {
        when(toolExecutor.findTool(toolName)).thenReturn(Optional.of(mock));
        when(mock.requiresConfirmation()).thenReturn(false);
    }

    // ── preScanAndBatchConfirm (tested via chat()) ────────────────────────────

    @Nested
    @DisplayName("preScanAndBatchConfirm")
    class PreScanAndBatchConfirm {

        @Test
        @DisplayName("skips batch confirmation when service reports unavailable")
        void skips_whenServiceUnavailable() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(false);
            // preScanAndBatchConfirm returns immediately – no findTool calls needed
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            // LLM: first returns 2 write tool calls, then final text
            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(
                            writeToolCall("call-1", "create_issue"),
                            writeToolCall("call-2", "update_issue"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Do stuff");

            verify(batchConfirmationService, never()).requestBatchConfirmation(anyList());
            verify(batchConfirmationService, never()).applyPreApprovedDecisions(anyMap());
        }

        @Test
        @DisplayName("skips batch confirmation when only one write tool call is present")
        void skips_whenOnlyOneWriteToolCall() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            stubWriteTool(writeTool1, "create_issue", "Create ABC-1");
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(writeToolCall("call-1", "create_issue"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Do stuff");

            verify(batchConfirmationService, never()).requestBatchConfirmation(anyList());
            verify(batchConfirmationService, never()).applyPreApprovedDecisions(anyMap());
        }

        @Test
        @DisplayName("skips batch confirmation when no write tool calls are present")
        void skips_whenNoWriteToolCalls() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            Tool readTool = mock(Tool.class);
            stubReadTool(readTool, "query_jira");
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("results"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(readToolCall("call-r", "query_jira"))))
                    .thenReturn(new ChatResponse("Here are results"));

            service.chat(new ArrayList<>(), "Show me issues");

            verify(batchConfirmationService, never()).requestBatchConfirmation(anyList());
        }

        @Test
        @DisplayName("triggers batch confirmation when two or more write tools are present")
        void triggers_whenMultipleWriteToolsPresent() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            stubWriteTool(writeTool1, "create_issue", "Create ABC-1");
            stubWriteTool(writeTool2, "update_issue", "Update ABC-2");
            Map<String, Boolean> decisions = Map.of("call-1", true, "call-2", false);
            when(batchConfirmationService.requestBatchConfirmation(anyList())).thenReturn(decisions);
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(
                            writeToolCall("call-1", "create_issue"),
                            writeToolCall("call-2", "update_issue"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Do two things");

            verify(batchConfirmationService).requestBatchConfirmation(anyList());
            verify(batchConfirmationService).applyPreApprovedDecisions(decisions);
        }

        @Test
        @DisplayName("passes correct WriteToolCallInfo (id, name, description) to the service")
        void passesCorrectWriteToolCallInfos() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            stubWriteTool(writeTool1, "create_issue", "Create ABC-1");
            stubWriteTool(writeTool2, "update_issue", "Update ABC-2");
            when(batchConfirmationService.requestBatchConfirmation(anyList()))
                    .thenReturn(Map.of("call-alpha", true, "call-beta", true));
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(
                            writeToolCall("call-alpha", "create_issue"),
                            writeToolCall("call-beta", "update_issue"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Create and update");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WriteToolCallInfo>> captor =
                    ArgumentCaptor.forClass(List.class);
            verify(batchConfirmationService).requestBatchConfirmation(captor.capture());

            List<WriteToolCallInfo> captured = captor.getValue();
            assertThat(captured).hasSize(2);
            assertThat(captured).extracting(WriteToolCallInfo::toolCallId)
                    .containsExactlyInAnyOrder("call-alpha", "call-beta");
            assertThat(captured).extracting(WriteToolCallInfo::toolName)
                    .containsExactlyInAnyOrder("create_issue", "update_issue");
            assertThat(captured).extracting(WriteToolCallInfo::callDescription)
                    .containsExactlyInAnyOrder("Create ABC-1", "Update ABC-2");
        }

        @Test
        @DisplayName("only counts write tools – read tools in the same response are ignored")
        void countsOnlyWriteTools_readToolsIgnored() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            stubWriteTool(writeTool1, "create_issue", "Create ABC-1");
            Tool readTool = mock(Tool.class);
            stubReadTool(readTool, "query_jira");
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            // One write tool + one read tool → only 1 write tool → no batch
            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(
                            writeToolCall("call-w", "create_issue"),
                            readToolCall("call-r", "query_jira"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Do one write and one read");

            verify(batchConfirmationService, never()).requestBatchConfirmation(anyList());
        }

        @Test
        @DisplayName("triggers batch confirmation when three write tools are present")
        void triggers_withThreeWriteTools() throws Exception {
            when(batchConfirmationService.isAvailable()).thenReturn(true);
            Tool writeTool3 = mock(Tool.class);
            stubWriteTool(writeTool1, "create_issue",   "Create ABC-1");
            stubWriteTool(writeTool2, "update_issue",   "Update ABC-2");
            stubWriteTool(writeTool3, "clone_issue",    "Clone ABC-3");
            when(batchConfirmationService.requestBatchConfirmation(anyList()))
                    .thenReturn(Map.of("c1", true, "c2", true, "c3", false));
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("ok"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(
                            writeToolCall("c1", "create_issue"),
                            writeToolCall("c2", "update_issue"),
                            writeToolCall("c3", "clone_issue"))))
                    .thenReturn(new ChatResponse("Done"));

            service.chat(new ArrayList<>(), "Do three writes");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WriteToolCallInfo>> captor = ArgumentCaptor.forClass(List.class);
            verify(batchConfirmationService).requestBatchConfirmation(captor.capture());
            assertThat(captor.getValue()).hasSize(3);
        }
    }

    // ── chat() general behaviour ──────────────────────────────────────────────

    @Nested
    @DisplayName("chat()")
    class ChatMethod {

        @Test
        @DisplayName("returns the LLM text when no tool calls are made")
        void returnsDirectResponse_whenNoToolCalls() throws Exception {
            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse("Hello there"));

            ChatResult result = service.chat(new ArrayList<>(), "Hi");

            assertThat(result.response()).isEqualTo("Hello there");
        }

        @Test
        @DisplayName("executes tool and returns final text response after tool loop")
        void executesTool_andReturnsFinalResponse() throws Exception {
            when(toolExecutor.executeToolCall(any(), any())).thenReturn(new ToolResult("JQL results"));

            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse(List.of(readToolCall("rc-1", "query_jira"))))
                    .thenReturn(new ChatResponse("Here are your issues"));

            ChatResult result = service.chat(new ArrayList<>(), "Show issues");

            assertThat(result.response()).isEqualTo("Here are your issues");
            verify(toolExecutor).executeToolCall(any(), any());
        }

        @Test
        @DisplayName("prepends persona primer to an empty history")
        void prependsPersonaPrimer_forEmptyHistory() throws Exception {
            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse("Ready"));

            List<Message> history = new ArrayList<>();
            service.chat(history, "Hello");

            // After the call, history should contain: [assistant(primer), user(msg), assistant(response)]
            assertThat(history).hasSizeGreaterThanOrEqualTo(2);
            assertThat(history.getFirst().getRole()).isEqualTo("assistant");
        }

        @Test
        @DisplayName("does NOT prepend persona primer when history is non-empty")
        void doesNotPrependPersonaPrimer_forNonEmptyHistory() throws Exception {
            when(llmClient.chat(anyList(), anyList()))
                    .thenReturn(new ChatResponse("Sure"));

            List<Message> history = new ArrayList<>();
            history.add(Message.user("Previous message"));
            history.add(Message.assistant("Previous response"));

            service.chat(history, "Follow-up");

            // Should not have injected an additional assistant message at index 0
            assertThat(history.getFirst().getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("throws InterruptedException when isCancelled flag is set before first LLM call")
        void throwsInterruptedException_whenFlagIsSetImmediately() throws Exception {
            // isCancelled returns true immediately → loop throws InterruptedException before LLM call
            assertThatThrownBy(() ->
                    service.chat(new ArrayList<>(), "Do stuff", null, null, () -> true)
            ).isInstanceOf(InterruptedException.class)
             .hasMessageContaining("Cancelled");

            verify(llmClient, never()).chat(anyList(), anyList());
        }
    }
}











