package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.jira.ConfirmationContext;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.orchestration.BatchConfirmationContext;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ExecutionContextManager")
class ExecutionContextManagerTest {

    private ExecutionContextManager contextManager;
    private ConfirmationContext.Handler mockConfirmationHandler;
    private java.util.function.Function<java.util.List<com.tschanz.aigeny.orchestration.WriteToolCallInfo>, 
                                        java.util.Map<String, Boolean>> mockBatchHandler;

    @BeforeEach
    void setUp() {
        contextManager = new ExecutionContextManager();
        mockConfirmationHandler = (desc, action) -> new ToolResult("mocked");
        mockBatchHandler = infos -> new HashMap<>();
    }

    @AfterEach
    void cleanup() {
        // Ensure all contexts are clean after each test
        contextManager.cleanupAllContexts();
    }

    @Nested
    @DisplayName("Context Setup")
    class ContextSetup {

        @Test
        @DisplayName("should set Jira token context")
        void shouldSetJiraTokenContext() {
            // Act
            contextManager.setupContexts(
                    "jira-token-123",
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(JiraTokenContext.get()).isEqualTo("jira-token-123");
        }

        @Test
        @DisplayName("should set Jira write enabled context")
        void shouldSetJiraWriteEnabledContext() {
            // Act
            contextManager.setupContexts(
                    "token",
                    true,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(JiraWriteContext.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set Jira write disabled context")
        void shouldSetJiraWriteDisabledContext() {
            // Act
            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should set Bitbucket token context")
        void shouldSetBitbucketTokenContext() {
            // Act
            contextManager.setupContexts(
                    null,
                    false,
                    "bitbucket-token-456",
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(BitbucketTokenContext.get()).isEqualTo("bitbucket-token-456");
        }

        @Test
        @DisplayName("should set confirmation handler")
        void shouldSetConfirmationHandler() {
            // Act
            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(ConfirmationContext.isAvailable()).isTrue();
            assertThat(ConfirmationContext.get()).isNotNull();
        }

        @Test
        @DisplayName("should set batch confirmation handler")
        void shouldSetBatchConfirmationHandler() {
            // Act
            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert
            assertThat(BatchConfirmationContext.isAvailable()).isTrue();
            assertThat(BatchConfirmationContext.get()).isNotNull();
        }

        @Test
        @DisplayName("should handle null tokens gracefully")
        void shouldHandleNullTokensGracefully() {
            // Act
            contextManager.setupContexts(
                    null,
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert - should not throw, contexts should be set to null
            assertThat(JiraTokenContext.get()).isNull();
            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear PendingJiraActionContext on setup")
        void shouldClearPendingJiraActionContextOnSetup() {
            // Arrange - add a pending action first
            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            PendingJiraActionContext.add(mockAction);
            List<PendingJiraAction> actions = PendingJiraActionContext.get();
            assertThat(actions).isNotNull().isNotEmpty();

            // Act
            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert - should be cleared (returns null when empty)
            List<PendingJiraAction> clearedActions = PendingJiraActionContext.get();
            assertThat(clearedActions).isNullOrEmpty();
        }

        @Test
        @DisplayName("should set all contexts in a single call")
        void shouldSetAllContextsInSingleCall() {
            // Act
            contextManager.setupContexts(
                    "jira-token",
                    true,
                    "bb-token",
                    mockConfirmationHandler,
                    mockBatchHandler
            );

            // Assert - all contexts should be set
            assertThat(JiraTokenContext.get()).isEqualTo("jira-token");
            assertThat(JiraWriteContext.isEnabled()).isTrue();
            assertThat(BitbucketTokenContext.get()).isEqualTo("bb-token");
            assertThat(ConfirmationContext.isAvailable()).isTrue();
            assertThat(BatchConfirmationContext.isAvailable()).isTrue();
            List<PendingJiraAction> actions = PendingJiraActionContext.get();
            assertThat(actions).isNullOrEmpty();
        }
    }

    @Nested
    @DisplayName("Context Cleanup")
    class ContextCleanup {

        @Test
        @DisplayName("should clear Jira token context")
        void shouldClearJiraTokenContext() {
            // Arrange
            JiraTokenContext.set("token");
            assertThat(JiraTokenContext.get()).isNotNull();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(JiraTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear Jira write context")
        void shouldClearJiraWriteContext() {
            // Arrange
            JiraWriteContext.set(true);
            assertThat(JiraWriteContext.isEnabled()).isTrue();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should clear Bitbucket token context")
        void shouldClearBitbucketTokenContext() {
            // Arrange
            BitbucketTokenContext.set("token");
            assertThat(BitbucketTokenContext.get()).isNotNull();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear confirmation handler")
        void shouldClearConfirmationHandler() {
            // Arrange
            ConfirmationContext.set(mockConfirmationHandler);
            assertThat(ConfirmationContext.isAvailable()).isTrue();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should clear batch confirmation handler")
        void shouldClearBatchConfirmationHandler() {
            // Arrange
            BatchConfirmationContext.set(mockBatchHandler);
            assertThat(BatchConfirmationContext.isAvailable()).isTrue();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(BatchConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should clear pending Jira actions")
        void shouldClearPendingJiraActions() {
            // Arrange
            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            PendingJiraActionContext.add(mockAction);
            List<PendingJiraAction> actions = PendingJiraActionContext.get();
            assertThat(actions).isNotNull().isNotEmpty();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            List<PendingJiraAction> clearedActions = PendingJiraActionContext.get();
            assertThat(clearedActions).isNull();
        }

        @Test
        @DisplayName("should clear pre-approved confirmations")
        void shouldClearPreApprovedConfirmations() {
            // Arrange
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put("call-1", true);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);
            assertThat(ConfirmationContext.hasPreApproved("call-1")).isTrue();

            // Act
            contextManager.cleanupAllContexts();

            // Assert
            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should clear all contexts in a single call")
        void shouldClearAllContextsInSingleCall() {
            // Arrange - setup all contexts
            contextManager.setupContexts(
                    "jira-token",
                    true,
                    "bb-token",
                    mockConfirmationHandler,
                    mockBatchHandler
            );
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put("call-1", true);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);

            // Act
            contextManager.cleanupAllContexts();

            // Assert - all contexts should be cleared
            assertThat(JiraTokenContext.get()).isNull();
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            assertThat(BitbucketTokenContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
            assertThat(BatchConfirmationContext.isAvailable()).isFalse();
            List<PendingJiraAction> actions = PendingJiraActionContext.get();
            assertThat(actions).isNull();
            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should not throw when cleaning already clean contexts")
        void shouldNotThrowWhenCleaningAlreadyCleanContexts() {
            // Arrange - no contexts set

            // Act & Assert - should not throw
            contextManager.cleanupAllContexts();
        }
    }

    @Nested
    @DisplayName("Handler Integration")
    class HandlerIntegration {

        @Test
        @DisplayName("should invoke confirmation handler when called via context")
        void shouldInvokeConfirmationHandlerWhenCalledViaContext() {
            // Arrange
            final boolean[] handlerCalled = {false};
            ConfirmationContext.Handler testHandler = (desc, action) -> {
                handlerCalled[0] = true;
                return new ToolResult("handler invoked");
            };

            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    testHandler,
                    mockBatchHandler
            );

            // Act
            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            ToolResult result = ConfirmationContext.get().requestConfirmation("test", mockAction);

            // Assert
            assertThat(handlerCalled[0]).isTrue();
            assertThat(result.getText()).isEqualTo("handler invoked");
        }

        @Test
        @DisplayName("should invoke batch confirmation handler when called via context")
        void shouldInvokeBatchConfirmationHandlerWhenCalledViaContext() {
            // Arrange
            final boolean[] handlerCalled = {false};
            java.util.function.Function<java.util.List<com.tschanz.aigeny.orchestration.WriteToolCallInfo>, 
                                        java.util.Map<String, Boolean>> testHandler = infos -> {
                handlerCalled[0] = true;
                Map<String, Boolean> decisions = new HashMap<>();
                decisions.put("call-1", true);
                return decisions;
            };

            contextManager.setupContexts(
                    "token",
                    false,
                    null,
                    mockConfirmationHandler,
                    testHandler
            );

            // Act
            List<WriteToolCallInfo> toolInfos = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create issue")
            );
            Map<String, Boolean> result = BatchConfirmationContext.get().apply(toolInfos);

            // Assert
            assertThat(handlerCalled[0]).isTrue();
            assertThat(result).containsEntry("call-1", true);
        }
    }

    @Nested
    @DisplayName("Memory Leak Prevention")
    class MemoryLeakPrevention {

        @Test
        @DisplayName("should prevent memory leak by cleaning up after exception")
        void shouldPreventMemoryLeakByCleaningUpAfterException() {
            try {
                // Arrange
                contextManager.setupContexts(
                        "token",
                        true,
                        "bb-token",
                        mockConfirmationHandler,
                        mockBatchHandler
                );

                // Simulate exception
                throw new RuntimeException("Simulated error");
            } catch (RuntimeException e) {
                // Expected
            } finally {
                // Act - cleanup in finally block
                contextManager.cleanupAllContexts();
            }

            // Assert - contexts should be clean
            assertThat(JiraTokenContext.get()).isNull();
            assertThat(BitbucketTokenContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should allow multiple setup-cleanup cycles")
        void shouldAllowMultipleSetupCleanupCycles() {
            // First cycle
            contextManager.setupContexts("token1", true, "bb1", mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token1");
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();

            // Second cycle
            contextManager.setupContexts("token2", false, "bb2", mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token2");
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();

            // Third cycle
            contextManager.setupContexts("token3", true, "bb3", mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token3");
            assertThat(JiraWriteContext.isEnabled()).isTrue();
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();
        }
    }
}
