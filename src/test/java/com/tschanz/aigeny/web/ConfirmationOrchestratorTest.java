package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.ToolResult;
import com.tschanz.aigeny.tool.jira.ConfirmationContext;
import com.tschanz.aigeny.tool.jira.JiraWriteContext;
import com.tschanz.aigeny.tool.jira.JiraWriteExecutor;
import com.tschanz.aigeny.tool.jira.PendingJiraAction;
import com.tschanz.aigeny.orchestration.CurrentToolCallContext;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmationOrchestrator")
class ConfirmationOrchestratorTest {

    @Mock private SessionConfirmationService sessionService;
    @Mock private JiraWriteExecutor jiraWriteExecutor;
    @Mock private HttpSession session;
    @Mock private SseEmitter emitter;
    @Mock private PendingJiraAction pendingAction;

    private ObjectMapper objectMapper;
    private ConfirmationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrator = new ConfirmationOrchestrator(sessionService, jiraWriteExecutor, objectMapper);
    }

    @AfterEach
    void cleanup() {
        // Clean up ThreadLocal contexts to avoid test pollution
        CurrentToolCallContext.clear();
        ConfirmationContext.clear();
        ConfirmationContext.clearPreApproved();
        JiraWriteContext.clear();
    }

    @Nested
    @DisplayName("Single Confirmation")
    class SingleConfirmation {

        @Test
        @DisplayName("should execute action when user confirms")
        void shouldExecuteActionWhenUserConfirms() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(pendingAction, "jira-token"))
                    .thenReturn("Issue PROJ-123 created successfully");

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Issue PROJ-123 created successfully");
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
            verify(emitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("should return declined message when user declines")
        void shouldReturnDeclinedMessageWhenUserDeclines() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(false);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("declined");
            verify(jiraWriteExecutor, never()).execute(any(), anyString());
        }

        @Test
        @DisplayName("should return timeout message when confirmation times out")
        void shouldReturnTimeoutMessageWhenConfirmationTimesOut() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new TimeoutException("Timed out"));
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).containsAnyOf("timeout", "Timed out");
            verify(jiraWriteExecutor, never()).execute(any(), anyString());
        }

        @Test
        @DisplayName("should handle execution failure gracefully")
        void shouldHandleExecutionFailureGracefully() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(pendingAction, "jira-token"))
                    .thenThrow(new RuntimeException("Jira API error"));

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("error");
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
        }

        @Test
        @DisplayName("should send confirmation_required SSE event")
        void shouldSendConfirmationRequiredSseEvent() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(false);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);

            // Act
            orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = 
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter, atLeastOnce()).send(eventCaptor.capture());
            
            // Verify at least one event was sent
            assertThat(eventCaptor.getAllValues()).isNotEmpty();
        }

        @Test
        @DisplayName("should handle InterruptedException gracefully")
        void shouldHandleInterruptedExceptionGracefully() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new InterruptedException("Thread interrupted"));
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue PROJ-123", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Confirmation error");
            verify(jiraWriteExecutor, never()).execute(any(), anyString());
        }
    }

    @Nested
    @DisplayName("Pre-Approved Confirmations")
    class PreApprovedConfirmations {

        @Test
        @DisplayName("should use pre-approved decision when available")
        void shouldUsePreApprovedDecisionWhenAvailable() throws Exception {
            // Arrange
            String toolCallId = "tool-call-123";
            CurrentToolCallContext.set(toolCallId);
            
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put(toolCallId, true);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);
            
            when(jiraWriteExecutor.execute(pendingAction, "jira-token"))
                    .thenReturn("Executed successfully");

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("Executed successfully");
            verify(sessionService, never()).createConfirmationFuture(any());
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
        }

        @Test
        @DisplayName("should decline when pre-approved decision is false")
        void shouldDeclineWhenPreApprovedDecisionIsFalse() throws Exception {
            // Arrange
            String toolCallId = "tool-call-456";
            CurrentToolCallContext.set(toolCallId);
            
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put(toolCallId, false);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getText()).contains("declined");
            verify(sessionService, never()).createConfirmationFuture(any());
            verify(jiraWriteExecutor, never()).execute(any(), anyString());
        }

        @Test
        @DisplayName("should fall back to normal confirmation when no pre-approval exists")
        void shouldFallBackToNormalConfirmationWhenNoPreApprovalExists() throws Exception {
            // Arrange
            String toolCallId = "tool-call-789";
            CurrentToolCallContext.set(toolCallId);
            // No pre-approvals set
            
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(pendingAction, "jira-token"))
                    .thenReturn("Executed after normal confirmation");

            // Act
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            // Assert
            assertThat(result).isNotNull();
            verify(sessionService).createConfirmationFuture(session);
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
        }
    }

    @Nested
    @DisplayName("Batch Confirmation")
    class BatchConfirmation {

        @Test
        @DisplayName("should return decisions map for all tool calls")
        void shouldReturnDecisionsMapForAllToolCalls() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_issue", "Update PROJ-2"),
                    new WriteToolCallInfo("call-3", "add_comment", "Add comment to PROJ-3")
            );
            
            Map<String, Boolean> decisions = Map.of(
                    "call-1", true,
                    "call-2", false,
                    "call-3", true
            );
            
            CompletableFuture<Map<String, Boolean>> future = CompletableFuture.completedFuture(decisions);
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).hasSize(3);
            assertThat(result.get("call-1")).isTrue();
            assertThat(result.get("call-2")).isFalse();
            assertThat(result.get("call-3")).isTrue();
        }

        @Test
        @DisplayName("should expand confirmAll shortcut to individual decisions")
        void shouldExpandConfirmAllShortcutToIndividualDecisions() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_issue", "Update PROJ-2")
            );
            
            Map<String, Boolean> shortcut = Map.of("__confirmAll__", true);
            
            CompletableFuture<Map<String, Boolean>> future = CompletableFuture.completedFuture(shortcut);
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get("call-1")).isTrue();
            assertThat(result.get("call-2")).isTrue();
        }

        @Test
        @DisplayName("should decline all on confirmAll=false")
        void shouldDeclineAllOnConfirmAllFalse() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_issue", "Update PROJ-2")
            );
            
            Map<String, Boolean> shortcut = Map.of("__confirmAll__", false);
            
            CompletableFuture<Map<String, Boolean>> future = CompletableFuture.completedFuture(shortcut);
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get("call-1")).isFalse();
            assertThat(result.get("call-2")).isFalse();
        }

        @Test
        @DisplayName("should decline all on timeout")
        void shouldDeclineAllOnTimeout() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_issue", "Update PROJ-2")
            );
            
            CompletableFuture<Map<String, Boolean>> future = new CompletableFuture<>();
            future.completeExceptionally(new TimeoutException("Timed out"));
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get("call-1")).isFalse();
            assertThat(result.get("call-2")).isFalse();
        }

        @Test
        @DisplayName("should decline all on exception")
        void shouldDeclineAllOnException() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1")
            );
            
            CompletableFuture<Map<String, Boolean>> future = new CompletableFuture<>();
            future.completeExceptionally(new ExecutionException(new RuntimeException("Error")));
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get("call-1")).isFalse();
        }

        @Test
        @DisplayName("should send batch_confirmation_required SSE event")
        void shouldSendBatchConfirmationRequiredSseEvent() throws Exception {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_issue", "Update PROJ-2")
            );
            
            Map<String, Boolean> decisions = Map.of("call-1", true, "call-2", false);
            CompletableFuture<Map<String, Boolean>> future = CompletableFuture.completedFuture(decisions);
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            ArgumentCaptor<SseEmitter.SseEventBuilder> eventCaptor = 
                    ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(emitter, atLeastOnce()).send(eventCaptor.capture());
            
            // Verify event was sent
            assertThat(eventCaptor.getAllValues()).isNotEmpty();
        }

        @Test
        @DisplayName("should handle empty tool calls list")
        void shouldHandleEmptyToolCallsList() {
            // Arrange
            List<WriteToolCallInfo> toolCalls = List.of();
            
            Map<String, Boolean> decisions = Map.of();
            CompletableFuture<Map<String, Boolean>> future = CompletableFuture.completedFuture(decisions);
            when(sessionService.createBatchConfirmationFuture(session)).thenReturn(future);

            // Act
            Map<String, Boolean> result = orchestrator.handleBatchConfirmation(emitter, session, toolCalls);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("SSE Event Handling")
    class SseEventHandling {

        @Test
        @DisplayName("should not throw when SSE send fails")
        void shouldNotThrowWhenSseSendFails() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(false);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            doThrow(new RuntimeException("SSE send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            // Act & Assert - should not throw
            ToolResult result = orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should send intermediate message when action executes successfully")
        void shouldSendIntermediateMessageWhenActionExecutesSuccessfully() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(pendingAction, "jira-token"))
                    .thenReturn("Issue created");

            // Act
            orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            // Assert - should send both confirmation_required and intermediate events
            verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("JiraWriteContext Integration")
    class JiraWriteContextIntegration {

        @Test
        @DisplayName("should set JiraWriteContext before executing action")
        void shouldSetJiraWriteContextBeforeExecutingAction() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(any(), anyString())).thenAnswer(invocation -> {
                // Verify context is set when executor runs
                Boolean writeEnabled = JiraWriteContext.isEnabled();
                assertThat(writeEnabled).isNotNull();
                return "Executed";
            });

            // Act
            orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", true,
                    "Create issue", pendingAction);

            // Assert
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
        }

        @Test
        @DisplayName("should pass jiraWriteEnabled flag correctly")
        void shouldPassJiraWriteEnabledFlagCorrectly() throws Exception {
            // Arrange
            CompletableFuture<Boolean> future = CompletableFuture.completedFuture(true);
            when(sessionService.createConfirmationFuture(session)).thenReturn(future);
            when(jiraWriteExecutor.execute(any(), anyString())).thenAnswer(invocation -> {
                Boolean writeEnabled = JiraWriteContext.isEnabled();
                assertThat(writeEnabled).isFalse(); // Should be false based on parameter
                return "Executed";
            });

            // Act - pass false for jiraWriteEnabled
            orchestrator.handleSingleConfirmation(
                    emitter, session, "jira-token", false,
                    "Create issue", pendingAction);

            // Assert
            verify(jiraWriteExecutor).execute(pendingAction, "jira-token");
        }
    }
}
