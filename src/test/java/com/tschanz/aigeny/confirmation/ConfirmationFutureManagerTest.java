package com.tschanz.aigeny.confirmation;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfirmationFutureManagerTest {

    @Mock
    private HttpSession session;

    private ConfirmationFutureManager manager;

    @BeforeEach
    void setUp() {
        manager = new ConfirmationFutureManager();
        when(session.getId()).thenReturn("test-session-123");
    }

    @Nested
    class SingleConfirmationManagement {

        @Test
        void createsSingleConfirmationFuture() {
            CompletableFuture<Boolean> future = manager.createSingleConfirmationFuture(session);

            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
            verify(session).setAttribute(eq("jiraConfirmationFuture"), eq(future));
        }

        @Test
        void resolvesSingleConfirmationWithConfirmed() {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(future);

            boolean resolved = manager.resolveSingleConfirmation(session, true);

            assertThat(resolved).isTrue();
            assertThat(future.isDone()).isTrue();
            assertThat(future.join()).isTrue();
            verify(session).removeAttribute("jiraConfirmationFuture");
        }

        @Test
        void resolvesSingleConfirmationWithDeclined() {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(future);

            boolean resolved = manager.resolveSingleConfirmation(session, false);

            assertThat(resolved).isTrue();
            assertThat(future.isDone()).isTrue();
            assertThat(future.join()).isFalse();
            verify(session).removeAttribute("jiraConfirmationFuture");
        }

        @Test
        void resolveSingleConfirmationReturnsFalseWhenNoFutureExists() {
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(null);

            boolean resolved = manager.resolveSingleConfirmation(session, true);

            assertThat(resolved).isFalse();
            verify(session, never()).removeAttribute(anyString());
        }

        @Test
        void singleConfirmationFutureCanBeAwaitedAcrossThreads() throws Exception {
            // Simulate real-world async scenario
            CompletableFuture<Boolean> future = manager.createSingleConfirmationFuture(session);
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(future);

            // Simulate confirmation coming from another thread (HTTP request)
            CompletableFuture<Void> resolver = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50); // Simulate network delay
                    manager.resolveSingleConfirmation(session, true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Wait for the confirmation result
            Boolean result = future.get(1, TimeUnit.SECONDS);

            assertThat(result).isTrue();
            resolver.join(); // Ensure resolver completed
        }
    }

    @Nested
    class BatchConfirmationManagement {

        @Test
        void createsBatchConfirmationFuture() {
            CompletableFuture<Map<String, Boolean>> future = manager.createBatchConfirmationFuture(session);

            assertThat(future).isNotNull();
            assertThat(future.isDone()).isFalse();
            verify(session).setAttribute(eq("jiraBatchConfirmFuture"), eq(future));
        }

        @Test
        void resolvesBatchConfirmationWithDecisions() {
            CompletableFuture<Map<String, Boolean>> future = new CompletableFuture<>();
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(future);

            Map<String, Boolean> decisions = Map.of(
                    "call-1", true,
                    "call-2", false,
                    "call-3", true
            );

            boolean resolved = manager.resolveBatchConfirmation(session, decisions);

            assertThat(resolved).isTrue();
            assertThat(future.isDone()).isTrue();
            assertThat(future.join()).isEqualTo(decisions);
            verify(session).removeAttribute("jiraBatchConfirmFuture");
        }

        @Test
        void resolveBatchConfirmationReturnsFalseWhenNoFutureExists() {
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(null);

            Map<String, Boolean> decisions = Map.of("call-1", true);
            boolean resolved = manager.resolveBatchConfirmation(session, decisions);

            assertThat(resolved).isFalse();
            verify(session, never()).removeAttribute(anyString());
        }

        @Test
        void batchConfirmationFutureCanBeAwaitedAcrossThreads() throws Exception {
            // Simulate real-world async scenario
            CompletableFuture<Map<String, Boolean>> future = manager.createBatchConfirmationFuture(session);
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(future);

            Map<String, Boolean> expectedDecisions = Map.of(
                    "call-1", true,
                    "call-2", false
            );

            // Simulate batch confirmation coming from another thread
            CompletableFuture<Void> resolver = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(50); // Simulate user thinking time
                    manager.resolveBatchConfirmation(session, expectedDecisions);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Wait for the batch confirmation result
            Map<String, Boolean> result = future.get(1, TimeUnit.SECONDS);

            assertThat(result).isEqualTo(expectedDecisions);
            resolver.join(); // Ensure resolver completed
        }

        @Test
        void handlesBatchConfirmationWithEmptyDecisions() {
            CompletableFuture<Map<String, Boolean>> future = new CompletableFuture<>();
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(future);

            Map<String, Boolean> emptyDecisions = Map.of();
            boolean resolved = manager.resolveBatchConfirmation(session, emptyDecisions);

            assertThat(resolved).isTrue();
            assertThat(future.join()).isEmpty();
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void canCreateMultipleSingleConfirmationFuturesSequentially() {
            CompletableFuture<Boolean> future1 = manager.createSingleConfirmationFuture(session);
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(future1);
            manager.resolveSingleConfirmation(session, true);

            reset(session);
            when(session.getId()).thenReturn("test-session-123");
            CompletableFuture<Boolean> future2 = manager.createSingleConfirmationFuture(session);
            when(session.getAttribute("jiraConfirmationFuture")).thenReturn(future2);
            manager.resolveSingleConfirmation(session, false);

            assertThat(future1.join()).isTrue();
            assertThat(future2.join()).isFalse();
        }

        @Test
        void canCreateMultipleBatchConfirmationFuturesSequentially() {
            CompletableFuture<Map<String, Boolean>> future1 = manager.createBatchConfirmationFuture(session);
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(future1);
            manager.resolveBatchConfirmation(session, Map.of("call-1", true));

            reset(session);
            when(session.getId()).thenReturn("test-session-123");
            CompletableFuture<Map<String, Boolean>> future2 = manager.createBatchConfirmationFuture(session);
            when(session.getAttribute("jiraBatchConfirmFuture")).thenReturn(future2);
            manager.resolveBatchConfirmation(session, Map.of("call-2", false));

            assertThat(future1.join()).containsEntry("call-1", true);
            assertThat(future2.join()).containsEntry("call-2", false);
        }
    }
}
