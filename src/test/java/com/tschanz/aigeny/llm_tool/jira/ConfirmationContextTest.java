package com.tschanz.aigeny.llm_tool.jira;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ConfirmationContext")
class ConfirmationContextTest {

    @AfterEach
    void tearDown() {
        ConfirmationContext.clear();
        ConfirmationContext.clearPreApproved();
    }

    // ── Handler management ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Handler lifecycle")
    class HandlerLifecycle {

        @Test
        @DisplayName("should return null when no handler is set")
        void shouldReturnNullWhenNoHandlerSet() {
            assertThat(ConfirmationContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("should store and retrieve handler")
        void shouldStoreAndRetrieveHandler() {
            ConfirmationContext.Handler handler = mock(ConfirmationContext.Handler.class);
            ConfirmationContext.set(handler);

            assertThat(ConfirmationContext.get()).isSameAs(handler);
            assertThat(ConfirmationContext.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("should clear handler")
        void shouldClearHandler() {
            ConfirmationContext.set(mock(ConfirmationContext.Handler.class));
            ConfirmationContext.clear();

            assertThat(ConfirmationContext.get()).isNull();
            assertThat(ConfirmationContext.isAvailable()).isFalse();
        }
    }

    // ── Pre-approval storage ────────────────────────────────────────────────

    @Nested
    @DisplayName("Pre-approval storage")
    class PreApprovalStorage {

        @Test
        @DisplayName("should return false for hasPreApproved when no decisions stored")
        void shouldReturnFalseWhenNoDecisionsStored() {
            assertThat(ConfirmationContext.hasPreApproved("any-id")).isFalse();
        }

        @Test
        @DisplayName("should store and detect pre-approved decision")
        void shouldStoreAndDetectPreApprovedDecision() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true, "call-2", false));

            assertThat(ConfirmationContext.hasPreApproved("call-1")).isTrue();
            assertThat(ConfirmationContext.hasPreApproved("call-2")).isTrue();
        }

        @Test
        @DisplayName("should return false for unknown tool call ID")
        void shouldReturnFalseForUnknownId() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));

            assertThat(ConfirmationContext.hasPreApproved("unknown-id")).isFalse();
        }

        @Test
        @DisplayName("should return confirmed=true for approved decision")
        void shouldReturnTrueForApprovedDecision() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));

            assertThat(ConfirmationContext.getPreApproved("call-1")).isTrue();
        }

        @Test
        @DisplayName("should return confirmed=false for declined decision")
        void shouldReturnFalseForDeclinedDecision() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", false));

            assertThat(ConfirmationContext.getPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should return false for getPreApproved when ID not present")
        void shouldReturnFalseForGetPreApprovedWhenIdAbsent() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));

            assertThat(ConfirmationContext.getPreApproved("other")).isFalse();
        }

        @Test
        @DisplayName("should clear pre-approved decisions")
        void shouldClearPreApprovedDecisions() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));
            ConfirmationContext.clearPreApproved();

            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
        }

        @Test
        @DisplayName("should replace previous decisions on second set call")
        void shouldReplacePreviousDecisions() {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-2", false));

            assertThat(ConfirmationContext.hasPreApproved("call-1")).isFalse();
            assertThat(ConfirmationContext.hasPreApproved("call-2")).isTrue();
        }
    }

    // ── Thread isolation ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Thread isolation")
    class ThreadIsolation {

        @Test
        @DisplayName("pre-approvals should not be visible in a different thread")
        void preApprovalsShouldBeThreadLocal() throws InterruptedException {
            ConfirmationContext.setPreApprovedDecisions(Map.of("call-1", true));

            AtomicReference<Boolean> visibleInOtherThread = new AtomicReference<>(false);
            Thread t = new Thread(() ->
                    visibleInOtherThread.set(ConfirmationContext.hasPreApproved("call-1")));
            t.start();
            t.join();

            assertThat(visibleInOtherThread.get()).isFalse();
        }

        @Test
        @DisplayName("handler should not be visible in a different thread")
        void handlerShouldBeThreadLocal() throws InterruptedException {
            ConfirmationContext.set(mock(ConfirmationContext.Handler.class));

            AtomicReference<Boolean> visibleInOtherThread = new AtomicReference<>(false);
            Thread t = new Thread(() ->
                    visibleInOtherThread.set(ConfirmationContext.isAvailable()));
            t.start();
            t.join();

            assertThat(visibleInOtherThread.get()).isFalse();
        }
    }
}
