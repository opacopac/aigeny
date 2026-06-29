package com.tschanz.aigeny.web;

import com.tschanz.aigeny.tool.ToolResult;
import com.tschanz.aigeny.tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.tool.jira.ConfirmationContext;
import com.tschanz.aigeny.tool.jira.JiraTokenContext;
import com.tschanz.aigeny.tool.jira.JiraWriteContext;
import com.tschanz.aigeny.tool.jira.PendingJiraAction;
import com.tschanz.aigeny.tool.jira.PendingJiraActionContext;
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
        // Use real providers so the manager tests the full delegation chain
        contextManager = new ExecutionContextManager(
                List.of(new JiraContextProvider(), new BitbucketContextProvider()));
        mockConfirmationHandler = (desc, action) -> new ToolResult("mocked");
        mockBatchHandler = infos -> new HashMap<>();
    }

    @AfterEach
    void cleanup() {
        contextManager.cleanupAllContexts();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Builds a mutable token map (allows null values, unlike Map.of). */
    private static Map<String, String> tokens(String jiraToken, String bitbucketToken) {
        Map<String, String> map = new HashMap<>();
        map.put(JiraContextProvider.KEY, jiraToken);
        map.put(BitbucketContextProvider.KEY, bitbucketToken);
        return map;
    }

    // ── Context Setup ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Context Setup")
    class ContextSetup {

        @Test
        @DisplayName("should set Jira token context")
        void shouldSetJiraTokenContext() {
            contextManager.setupContexts(tokens("jira-token-123", null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(JiraTokenContext.get()).isEqualTo("jira-token-123");
        }

        @Test
        @DisplayName("should set Jira write enabled context")
        void shouldSetJiraWriteEnabledContext() {
            contextManager.setupContexts(tokens("token", null), true,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(JiraWriteContext.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should set Jira write disabled context")
        void shouldSetJiraWriteDisabledContext() {
            contextManager.setupContexts(tokens("token", null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should set Bitbucket token context")
        void shouldSetBitbucketTokenContext() {
            contextManager.setupContexts(tokens(null, "bitbucket-token-456"), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(BitbucketTokenContext.get()).isEqualTo("bitbucket-token-456");
        }

        @Test
        @DisplayName("should set confirmation handler")
        void shouldSetConfirmationHandler() {
            contextManager.setupContexts(tokens("token", null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(ConfirmationContext.isAvailable()).isTrue();
            assertThat(ConfirmationContext.get()).isNotNull();
        }

        @Test
        @DisplayName("should set batch confirmation handler")
        void shouldSetBatchConfirmationHandler() {
            contextManager.setupContexts(tokens("token", null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(BatchConfirmationContext.isAvailable()).isTrue();
            assertThat(BatchConfirmationContext.get()).isNotNull();
        }

        @Test
        @DisplayName("should handle null tokens gracefully")
        void shouldHandleNullTokensGracefully() {
            contextManager.setupContexts(tokens(null, null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(JiraTokenContext.get()).isNull();
            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear PendingJiraActionContext on setup")
        void shouldClearPendingJiraActionContextOnSetup() {
            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            PendingJiraActionContext.add(mockAction);
            assertThat(PendingJiraActionContext.get()).isNotNull().isNotEmpty();

            contextManager.setupContexts(tokens("token", null), false,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(PendingJiraActionContext.get()).isNullOrEmpty();
        }

        @Test
        @DisplayName("should set all contexts in a single call")
        void shouldSetAllContextsInSingleCall() {
            contextManager.setupContexts(tokens("jira-token", "bb-token"), true,
                    mockConfirmationHandler, mockBatchHandler);

            assertThat(JiraTokenContext.get()).isEqualTo("jira-token");
            assertThat(JiraWriteContext.isEnabled()).isTrue();
            assertThat(BitbucketTokenContext.get()).isEqualTo("bb-token");
            assertThat(ConfirmationContext.isAvailable()).isTrue();
            assertThat(BatchConfirmationContext.isAvailable()).isTrue();
            assertThat(PendingJiraActionContext.get()).isNullOrEmpty();
        }

        @Test
        @DisplayName("unknown provider key in map is silently ignored")
        void unknownKeyInMapIsIgnored() {
            Map<String, String> tokensWithExtra = tokens("jira-token", "bb-token");
            tokensWithExtra.put("confluence", "confluence-token"); // no provider registered

            contextManager.setupContexts(tokensWithExtra, false,
                    mockConfirmationHandler, mockBatchHandler);

            // Existing providers still set correctly
            assertThat(JiraTokenContext.get()).isEqualTo("jira-token");
            assertThat(BitbucketTokenContext.get()).isEqualTo("bb-token");
        }
    }

    // ── Context Cleanup ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Context Cleanup")
    class ContextCleanup {

        @Test
        @DisplayName("should clear Jira token context")
        void shouldClearJiraTokenContext() {
            JiraTokenContext.set("token");

            contextManager.cleanupAllContexts();

            assertThat(JiraTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear Jira write context")
        void shouldClearJiraWriteContext() {
            JiraWriteContext.set(true);

            contextManager.cleanupAllContexts();

            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should clear Bitbucket token context")
        void shouldClearBitbucketTokenContext() {
            BitbucketTokenContext.set("token");

            contextManager.cleanupAllContexts();

            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear confirmation handler")
        void shouldClearConfirmationHandler() {
            ConfirmationContext.set(mockConfirmationHandler);

            contextManager.cleanupAllContexts();

            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should clear batch confirmation handler")
        void shouldClearBatchConfirmationHandler() {
            BatchConfirmationContext.set(mockBatchHandler);

            contextManager.cleanupAllContexts();

            assertThat(BatchConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should clear pending Jira actions")
        void shouldClearPendingJiraActions() {
            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            PendingJiraActionContext.add(mockAction);

            contextManager.cleanupAllContexts();

            assertThat(PendingJiraActionContext.get()).isNull();
        }

        @Test
        @DisplayName("should clear pre-approved confirmations")
        void shouldClearPreApprovedConfirmations() {
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put("call-1", true);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);

            contextManager.cleanupAllContexts();

            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should clear all contexts in a single call")
        void shouldClearAllContextsInSingleCall() {
            contextManager.setupContexts(tokens("jira-token", "bb-token"), true,
                    mockConfirmationHandler, mockBatchHandler);
            Map<String, Boolean> preApprovals = new HashMap<>();
            preApprovals.put("call-1", true);
            ConfirmationContext.setPreApprovedDecisions(preApprovals);

            contextManager.cleanupAllContexts();

            assertThat(JiraTokenContext.get()).isNull();
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            assertThat(BitbucketTokenContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
            assertThat(BatchConfirmationContext.isAvailable()).isFalse();
            assertThat(PendingJiraActionContext.get()).isNull();
            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should not throw when cleaning already clean contexts")
        void shouldNotThrowWhenCleaningAlreadyCleanContexts() {
            contextManager.cleanupAllContexts(); // nothing set – must not throw
        }
    }

    // ── Handler Integration ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Handler Integration")
    class HandlerIntegration {

        @Test
        @DisplayName("should invoke confirmation handler when called via context")
        void shouldInvokeConfirmationHandlerWhenCalledViaContext() {
            final boolean[] handlerCalled = {false};
            ConfirmationContext.Handler testHandler = (desc, action) -> {
                handlerCalled[0] = true;
                return new ToolResult("handler invoked");
            };

            contextManager.setupContexts(tokens("token", null), false,
                    testHandler, mockBatchHandler);

            PendingJiraAction mockAction = mock(PendingJiraAction.class);
            ToolResult result = ConfirmationContext.get().requestConfirmation("test", mockAction);

            assertThat(handlerCalled[0]).isTrue();
            assertThat(result.getText()).isEqualTo("handler invoked");
        }

        @Test
        @DisplayName("should invoke batch confirmation handler when called via context")
        void shouldInvokeBatchConfirmationHandlerWhenCalledViaContext() {
            final boolean[] handlerCalled = {false};
            java.util.function.Function<java.util.List<WriteToolCallInfo>,
                                        java.util.Map<String, Boolean>> testHandler = infos -> {
                handlerCalled[0] = true;
                Map<String, Boolean> decisions = new HashMap<>();
                decisions.put("call-1", true);
                return decisions;
            };

            contextManager.setupContexts(tokens("token", null), false,
                    mockConfirmationHandler, testHandler);

            List<WriteToolCallInfo> toolInfos = List.of(
                    new WriteToolCallInfo("call-1", "create_issue", "Create issue"));
            Map<String, Boolean> result = BatchConfirmationContext.get().apply(toolInfos);

            assertThat(handlerCalled[0]).isTrue();
            assertThat(result).containsEntry("call-1", true);
        }
    }

    // ── Memory Leak Prevention ────────────────────────────────────────────────

    @Nested
    @DisplayName("Memory Leak Prevention")
    class MemoryLeakPrevention {

        @Test
        @DisplayName("should prevent memory leak by cleaning up after exception")
        void shouldPreventMemoryLeakByCleaningUpAfterException() {
            try {
                contextManager.setupContexts(tokens("token", "bb-token"), true,
                        mockConfirmationHandler, mockBatchHandler);
                throw new RuntimeException("Simulated error");
            } catch (RuntimeException e) {
                // expected
            } finally {
                contextManager.cleanupAllContexts();
            }

            assertThat(JiraTokenContext.get()).isNull();
            assertThat(BitbucketTokenContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should allow multiple setup-cleanup cycles")
        void shouldAllowMultipleSetupCleanupCycles() {
            contextManager.setupContexts(tokens("token1", "bb1"), true, mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token1");
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();

            contextManager.setupContexts(tokens("token2", "bb2"), false, mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token2");
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();

            contextManager.setupContexts(tokens("token3", "bb3"), true, mockConfirmationHandler, mockBatchHandler);
            assertThat(JiraTokenContext.get()).isEqualTo("token3");
            assertThat(JiraWriteContext.isEnabled()).isTrue();
            contextManager.cleanupAllContexts();
            assertThat(JiraTokenContext.get()).isNull();
        }
    }

    // ── OCP: provider extensibility ───────────────────────────────────────────

    @Nested
    @DisplayName("ContextProvider extensibility (OCP)")
    class ProviderExtensibility {

        @Test
        @DisplayName("manager with zero providers still sets up confirmation contexts")
        void emptyProviderList_stillSetsConfirmationContexts() {
            ExecutionContextManager managerWithNoProviders = new ExecutionContextManager(List.of());
            try {
                managerWithNoProviders.setupContexts(tokens("jira-token", "bb-token"), true,
                        mockConfirmationHandler, mockBatchHandler);

                assertThat(ConfirmationContext.isAvailable()).isTrue();
                assertThat(BatchConfirmationContext.isAvailable()).isTrue();
                // Token contexts are NOT set because no providers are registered
                assertThat(JiraTokenContext.get()).isNull();
                assertThat(BitbucketTokenContext.get()).isNull();
            } finally {
                managerWithNoProviders.cleanupAllContexts();
            }
        }

        @Test
        @DisplayName("custom provider is called by manager and can set its own ThreadLocal")
        void customProvider_isInvokedByManager() {
            // Simulate adding a third integration without modifying ExecutionContextManager
            final String[] capturedToken = {null};
            final boolean[] capturedWrite = {false};
            ContextProvider customProvider = new ContextProvider() {
                @Override public String getKey() { return "custom"; }
                @Override public void setup(String token, boolean writeEnabled) {
                    capturedToken[0] = token;
                    capturedWrite[0] = writeEnabled;
                }
                @Override public void cleanup() { capturedToken[0] = null; }
            };

            ExecutionContextManager extendedManager = new ExecutionContextManager(
                    List.of(new JiraContextProvider(), new BitbucketContextProvider(), customProvider));

            Map<String, String> tokensWithCustom = tokens("jira", "bb");
            tokensWithCustom.put("custom", "custom-secret");

            try {
                extendedManager.setupContexts(tokensWithCustom, false,
                        mockConfirmationHandler, mockBatchHandler);

                assertThat(capturedToken[0]).isEqualTo("custom-secret");
                assertThat(capturedWrite[0]).isFalse();
            } finally {
                extendedManager.cleanupAllContexts();
            }
        }
    }
}
