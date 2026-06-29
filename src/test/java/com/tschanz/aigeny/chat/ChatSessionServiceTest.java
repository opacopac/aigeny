package com.tschanz.aigeny.chat;
import com.tschanz.aigeny.confirmation.ConfirmationFutureManager;
import com.tschanz.aigeny.session.CancellationManager;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.tool.QueryResult;
import com.tschanz.aigeny.jira.confirmation.PendingJiraAction;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatSessionService")
class ChatSessionServiceTest {

    @Mock
    private HttpSession session;

    @Mock
    private ConfirmationFutureManager confirmationFutureManager;

    @Mock
    private CancellationManager cancellationManager;

    @Mock
    private HistoryManager historyManager;

    private ChatSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new ChatSessionService(confirmationFutureManager, cancellationManager, historyManager);
        lenient().when(session.getId()).thenReturn("test-session-123");
    }

    @Nested
    @DisplayName("Chat History Management")
    class ChatHistoryManagement {

        @Test
        @DisplayName("should delegate getOrCreateHistory to HistoryManager")
        void shouldDelegateGetOrCreateHistoryToHistoryManager() {
            // Given
            List<Message> expectedHistory = new ArrayList<>();
            expectedHistory.add(Message.user("Test"));
            when(historyManager.getOrCreateHistory(session)).thenReturn(expectedHistory);

            // When
            List<Message> history = sessionService.getOrCreateHistory(session);

            // Then
            assertThat(history).isSameAs(expectedHistory);
            verify(historyManager).getOrCreateHistory(session);
        }

        @Test
        @DisplayName("should delegate clearHistory to HistoryManager")
        void shouldDelegateClearHistoryToHistoryManager() {
            // When
            sessionService.clearHistory(session);

            // Then
            verify(historyManager).clearHistory(session);
        }
    }

    @Nested
    @DisplayName("Query Result Management")
    class QueryResultManagement {

        @Test
        @DisplayName("should store query result in session")
        void shouldStoreQueryResultInSession() {
            // Given
            QueryResult result = new QueryResult("Test", List.of("COL1"),
                    List.of(java.util.Map.of("COL1", "value")));

            // When
            sessionService.setLastQueryResult(session, result);

            // Then
            verify(session).setAttribute("lastQueryResult", result);
        }

        @Test
        @DisplayName("should retrieve stored query result")
        void shouldRetrieveStoredQueryResult() {
            // Given
            QueryResult result = new QueryResult("Test", List.of("COL1"),
                    List.of(java.util.Map.of("COL1", "value")));
            when(session.getAttribute("lastQueryResult")).thenReturn(result);

            // When
            QueryResult retrieved = sessionService.getLastQueryResult(session);

            // Then
            assertThat(retrieved).isSameAs(result);
        }

        @Test
        @DisplayName("should return null when no query result exists")
        void shouldReturnNullWhenNoQueryResultExists() {
            // Given
            when(session.getAttribute("lastQueryResult")).thenReturn(null);

            // When
            QueryResult retrieved = sessionService.getLastQueryResult(session);

            // Then
            assertThat(retrieved).isNull();
        }

        @Test
        @DisplayName("should detect when query result exists")
        void shouldDetectWhenQueryResultExists() {
            // Given
            QueryResult result = new QueryResult("Test", List.of("COL1"),
                    List.of(java.util.Map.of("COL1", "value")));
            when(session.getAttribute("lastQueryResult")).thenReturn(result);

            // When
            boolean hasResult = sessionService.hasQueryResult(session);

            // Then
            assertThat(hasResult).isTrue();
        }

        @Test
        @DisplayName("should detect when query result does not exist")
        void shouldDetectWhenQueryResultDoesNotExist() {
            // Given
            when(session.getAttribute("lastQueryResult")).thenReturn(null);

            // When
            boolean hasResult = sessionService.hasQueryResult(session);

            // Then
            assertThat(hasResult).isFalse();
        }

        @Test
        @DisplayName("should detect when query result is empty")
        void shouldDetectWhenQueryResultIsEmpty() {
            // Given
            QueryResult emptyResult = new QueryResult("Test", List.of("COL1"), List.of());
            when(session.getAttribute("lastQueryResult")).thenReturn(emptyResult);

            // When
            boolean hasResult = sessionService.hasQueryResult(session);

            // Then
            assertThat(hasResult).isFalse();
        }

        @Test
        @DisplayName("should clear query result from session")
        void shouldClearQueryResultFromSession() {
            // When
            sessionService.clearLastQueryResult(session);

            // Then
            verify(session).removeAttribute("lastQueryResult");
        }
    }

    @Nested
    @DisplayName("Pending Jira Actions Management")
    class PendingJiraActionsManagement {

        @Test
        @DisplayName("should store pending actions in session")
        void shouldStorePendingActionsInSession() {
            // Given
            List<PendingJiraAction> actions = List.of(
                    new PendingJiraAction(PendingJiraAction.ActionType.UPDATE_ISSUE,
                            "PROJ-123", java.util.Map.of(), "Update issue")
            );

            // When
            sessionService.setPendingJiraActions(session, actions);

            // Then
            verify(session).setAttribute("pendingJiraAction", actions);
        }

        @Test
        @DisplayName("should retrieve pending actions from session")
        void shouldRetrievePendingActionsFromSession() {
            // Given
            List<PendingJiraAction> actions = List.of(
                    new PendingJiraAction(PendingJiraAction.ActionType.ADD_COMMENT,
                            "PROJ-456", java.util.Map.of(), "Add comment")
            );
            when(session.getAttribute("pendingJiraAction")).thenReturn(actions);

            // When
            List<PendingJiraAction> retrieved = sessionService.getPendingJiraActions(session);

            // Then
            assertThat(retrieved).isSameAs(actions);
        }

        @Test
        @DisplayName("should return null when no pending actions exist")
        void shouldReturnNullWhenNoPendingActionsExist() {
            // Given
            when(session.getAttribute("pendingJiraAction")).thenReturn(null);

            // When
            List<PendingJiraAction> retrieved = sessionService.getPendingJiraActions(session);

            // Then
            assertThat(retrieved).isNull();
        }

        @Test
        @DisplayName("should detect when pending actions exist")
        void shouldDetectWhenPendingActionsExist() {
            // Given
            List<PendingJiraAction> actions = List.of(
                    new PendingJiraAction(PendingJiraAction.ActionType.CREATE_ISSUE,
                            null, java.util.Map.of(), "Create issue")
            );
            when(session.getAttribute("pendingJiraAction")).thenReturn(actions);

            // When
            boolean hasActions = sessionService.hasPendingJiraActions(session);

            // Then
            assertThat(hasActions).isTrue();
        }

        @Test
        @DisplayName("should detect when no pending actions exist")
        void shouldDetectWhenNoPendingActionsExist() {
            // Given
            when(session.getAttribute("pendingJiraAction")).thenReturn(null);

            // When
            boolean hasActions = sessionService.hasPendingJiraActions(session);

            // Then
            assertThat(hasActions).isFalse();
        }

        @Test
        @DisplayName("should detect when pending actions list is empty")
        void shouldDetectWhenPendingActionsListIsEmpty() {
            // Given
            when(session.getAttribute("pendingJiraAction")).thenReturn(List.of());

            // When
            boolean hasActions = sessionService.hasPendingJiraActions(session);

            // Then
            assertThat(hasActions).isFalse();
        }

        @Test
        @DisplayName("should clear pending actions from session")
        void shouldClearPendingActionsFromSession() {
            // When
            sessionService.clearPendingJiraActions(session);

            // Then
            verify(session).removeAttribute("pendingJiraAction");
        }
    }

    @Nested
    @DisplayName("Jira Write Mode Management")
    class JiraWriteModeManagement {

        @Test
        @DisplayName("should enable Jira write mode")
        void shouldEnableJiraWriteMode() {
            // When
            sessionService.setJiraWriteMode(session, true);

            // Then
            verify(session).setAttribute("jiraWriteEnabled", true);
        }

        @Test
        @DisplayName("should disable Jira write mode")
        void shouldDisableJiraWriteMode() {
            // When
            sessionService.setJiraWriteMode(session, false);

            // Then
            verify(session).setAttribute("jiraWriteEnabled", false);
        }

        @Test
        @DisplayName("should detect when write mode is enabled")
        void shouldDetectWhenWriteModeIsEnabled() {
            // Given
            when(session.getAttribute("jiraWriteEnabled")).thenReturn(true);

            // When
            boolean enabled = sessionService.isJiraWriteModeEnabled(session);

            // Then
            assertThat(enabled).isTrue();
        }

        @Test
        @DisplayName("should detect when write mode is disabled")
        void shouldDetectWhenWriteModeIsDisabled() {
            // Given
            when(session.getAttribute("jiraWriteEnabled")).thenReturn(false);

            // When
            boolean enabled = sessionService.isJiraWriteModeEnabled(session);

            // Then
            assertThat(enabled).isFalse();
        }

        @Test
        @DisplayName("should detect when write mode is not set")
        void shouldDetectWhenWriteModeIsNotSet() {
            // Given
            when(session.getAttribute("jiraWriteEnabled")).thenReturn(null);

            // When
            boolean enabled = sessionService.isJiraWriteModeEnabled(session);

            // Then
            assertThat(enabled).isFalse();
        }
    }

    @Nested
    @DisplayName("Cancel Flag Management")
    class CancelFlagManagement {

        @Test
        @DisplayName("should delegate cancel flag creation to CancellationManager")
        void shouldDelegateCancelFlagCreation() {
            // Given
            AtomicBoolean expectedFlag = new AtomicBoolean(false);
            when(cancellationManager.createCancelFlag(session)).thenReturn(expectedFlag);

            // When
            AtomicBoolean flag = sessionService.createCancelFlag(session);

            // Then
            assertThat(flag).isSameAs(expectedFlag);
            verify(cancellationManager).createCancelFlag(session);
        }

        @Test
        @DisplayName("should delegate cancel flag retrieval to CancellationManager")
        void shouldDelegateCancelFlagRetrieval() {
            // Given
            AtomicBoolean storedFlag = new AtomicBoolean(false);
            when(cancellationManager.getCancelFlag(session)).thenReturn(storedFlag);

            // When
            AtomicBoolean retrieved = sessionService.getCancelFlag(session);

            // Then
            assertThat(retrieved).isSameAs(storedFlag);
            verify(cancellationManager).getCancelFlag(session);
        }

        @Test
        @DisplayName("should delegate cancellation triggering to CancellationManager")
        void shouldDelegateCancellationTriggering() {
            // Given
            when(cancellationManager.triggerCancellation(session)).thenReturn(true);

            // When
            boolean triggered = sessionService.triggerCancellation(session);

            // Then
            assertThat(triggered).isTrue();
            verify(cancellationManager).triggerCancellation(session);
        }

        @Test
        @DisplayName("should return false when triggering without flag via CancellationManager")
        void shouldReturnFalseWhenTriggeringWithoutFlag() {
            // Given
            when(cancellationManager.triggerCancellation(session)).thenReturn(false);

            // When
            boolean triggered = sessionService.triggerCancellation(session);

            // Then
            assertThat(triggered).isFalse();
            verify(cancellationManager).triggerCancellation(session);
        }

        @Test
        @DisplayName("should delegate cancel flag clearing to CancellationManager")
        void shouldDelegateCancelFlagClearing() {
            // When
            sessionService.clearCancelFlag(session);

            // Then
            verify(cancellationManager).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Session Cleanup")
    class SessionCleanup {

        @Test
        @DisplayName("should clear all chat data from session")
        void shouldClearAllChatDataFromSession() {
            // When
            sessionService.clearAll(session);

            // Then
            verify(historyManager).clearHistory(session);
            verify(session).removeAttribute("lastQueryResult");
            verify(session).removeAttribute("pendingJiraAction");
            verify(cancellationManager).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Batch Confirmation Future Management")
    class BatchConfirmationFutureManagement {

        @Test
        @DisplayName("should delegate batch confirmation future creation to ConfirmationFutureManager")
        void shouldDelegateBatchConfirmationFutureCreation() {
            // Given
            CompletableFuture<Map<String, Boolean>> expectedFuture = new CompletableFuture<>();
            when(confirmationFutureManager.createBatchConfirmationFuture(session)).thenReturn(expectedFuture);

            // When
            CompletableFuture<Map<String, Boolean>> future = sessionService.createBatchConfirmationFuture(session);

            // Then
            assertThat(future).isSameAs(expectedFuture);
            verify(confirmationFutureManager).createBatchConfirmationFuture(session);
        }

        @Test
        @DisplayName("should delegate batch confirmation resolution to ConfirmationFutureManager")
        void shouldDelegateBatchConfirmationResolution() {
            // Given
            Map<String, Boolean> decisions = Map.of("call-1", true, "call-2", false);
            when(confirmationFutureManager.resolveBatchConfirmation(session, decisions)).thenReturn(true);

            // When
            boolean resolved = sessionService.resolveBatchConfirmation(session, decisions);

            // Then
            assertThat(resolved).isTrue();
            verify(confirmationFutureManager).resolveBatchConfirmation(session, decisions);
        }

        @Test
        @DisplayName("should return false when no batch future is pending")
        void shouldReturnFalseWhenNoBatchFuturePending() {
            // Given
            when(confirmationFutureManager.resolveBatchConfirmation(session, Map.of())).thenReturn(false);

            // When
            boolean resolved = sessionService.resolveBatchConfirmation(session, Map.of());

            // Then
            assertThat(resolved).isFalse();
            verify(confirmationFutureManager).resolveBatchConfirmation(session, Map.of());
        }
    }

    @Nested
    @DisplayName("Single Confirmation Future Management")
    class SingleConfirmationFutureManagement {

        @Test
        @DisplayName("should delegate single confirmation future creation to ConfirmationFutureManager")
        void shouldDelegateSingleConfirmationFutureCreation() {
            // Given
            CompletableFuture<Boolean> expectedFuture = new CompletableFuture<>();
            when(confirmationFutureManager.createSingleConfirmationFuture(session)).thenReturn(expectedFuture);

            // When
            CompletableFuture<Boolean> future = sessionService.createConfirmationFuture(session);

            // Then
            assertThat(future).isSameAs(expectedFuture);
            verify(confirmationFutureManager).createSingleConfirmationFuture(session);
        }

        @Test
        @DisplayName("should delegate single confirmation resolution to ConfirmationFutureManager")
        void shouldDelegateSingleConfirmationResolution() {
            // Given
            when(confirmationFutureManager.resolveSingleConfirmation(session, true)).thenReturn(true);

            // When
            boolean resolved = sessionService.resolveConfirmation(session, true);

            // Then
            assertThat(resolved).isTrue();
            verify(confirmationFutureManager).resolveSingleConfirmation(session, true);
        }

        @Test
        @DisplayName("should return false when no single future is pending")
        void shouldReturnFalseWhenNoSingleFuturePending() {
            // Given
            when(confirmationFutureManager.resolveSingleConfirmation(session, false)).thenReturn(false);

            // When
            boolean resolved = sessionService.resolveConfirmation(session, false);

            // Then
            assertThat(resolved).isFalse();
            verify(confirmationFutureManager).resolveSingleConfirmation(session, false);
        }
    }
}



