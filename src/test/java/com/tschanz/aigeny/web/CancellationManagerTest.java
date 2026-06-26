package com.tschanz.aigeny.web;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancellationManagerTest {

    @Mock
    private HttpSession session;

    private CancellationManager manager;

    @BeforeEach
    void setUp() {
        manager = new CancellationManager();
        lenient().when(session.getId()).thenReturn("test-session-123");
    }

    @Nested
    class CancelFlagCreation {

        @Test
        void createsCancelFlagInNotCancelledState() {
            AtomicBoolean flag = manager.createCancelFlag(session);

            assertThat(flag).isNotNull();
            assertThat(flag.get()).isFalse();
            verify(session).setAttribute(eq("chatCancelFlag"), eq(flag));
        }

        @Test
        void createsCancelFlagEachTimeMethodIsCalled() {
            AtomicBoolean flag1 = manager.createCancelFlag(session);
            AtomicBoolean flag2 = manager.createCancelFlag(session);

            assertThat(flag1).isNotSameAs(flag2);
            verify(session, times(2)).setAttribute(eq("chatCancelFlag"), any(AtomicBoolean.class));
        }
    }

    @Nested
    class CancelFlagRetrieval {

        @Test
        void retrievesStoredCancelFlag() {
            AtomicBoolean storedFlag = new AtomicBoolean(false);
            when(session.getAttribute("chatCancelFlag")).thenReturn(storedFlag);

            AtomicBoolean retrieved = manager.getCancelFlag(session);

            assertThat(retrieved).isSameAs(storedFlag);
        }

        @Test
        void returnsNullWhenNoCancelFlagExists() {
            when(session.getAttribute("chatCancelFlag")).thenReturn(null);

            AtomicBoolean retrieved = manager.getCancelFlag(session);

            assertThat(retrieved).isNull();
        }
    }

    @Nested
    class CancellationTriggering {

        @Test
        void triggersCancellationWhenFlagExists() {
            AtomicBoolean flag = new AtomicBoolean(false);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            boolean triggered = manager.triggerCancellation(session);

            assertThat(triggered).isTrue();
            assertThat(flag.get()).isTrue();
        }

        @Test
        void returnsFalseWhenNoFlagExists() {
            when(session.getAttribute("chatCancelFlag")).thenReturn(null);

            boolean triggered = manager.triggerCancellation(session);

            assertThat(triggered).isFalse();
        }

        @Test
        void canTriggerCancellationMultipleTimes() {
            AtomicBoolean flag = new AtomicBoolean(false);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            boolean triggered1 = manager.triggerCancellation(session);
            boolean triggered2 = manager.triggerCancellation(session);

            assertThat(triggered1).isTrue();
            assertThat(triggered2).isTrue();
            assertThat(flag.get()).isTrue();
        }
    }

    @Nested
    class CancellationChecking {

        @Test
        void returnsTrueWhenCancellationWasRequested() {
            AtomicBoolean flag = new AtomicBoolean(true);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            boolean requested = manager.isCancellationRequested(session);

            assertThat(requested).isTrue();
        }

        @Test
        void returnsFalseWhenCancellationNotRequested() {
            AtomicBoolean flag = new AtomicBoolean(false);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            boolean requested = manager.isCancellationRequested(session);

            assertThat(requested).isFalse();
        }

        @Test
        void returnsFalseWhenNoFlagExists() {
            when(session.getAttribute("chatCancelFlag")).thenReturn(null);

            boolean requested = manager.isCancellationRequested(session);

            assertThat(requested).isFalse();
        }
    }

    @Nested
    class CancelFlagCleanup {

        @Test
        void clearsCancelFlagFromSession() {
            manager.clearCancelFlag(session);

            verify(session).removeAttribute("chatCancelFlag");
        }

        @Test
        void clearingNonExistentFlagDoesNotThrow() {
            // Should not throw even if no flag exists
            manager.clearCancelFlag(session);

            verify(session).removeAttribute("chatCancelFlag");
        }
    }

    @Nested
    class ThreadSafety {

        @Test
        void cancelFlagIsThreadSafe() throws Exception {
            // Simulate real-world scenario: orchestration thread checking flag
            // while HTTP thread sets it
            AtomicBoolean flag = manager.createCancelFlag(session);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            // Start a thread that checks the flag repeatedly
            Thread checker = new Thread(() -> {
                for (int i = 0; i < 1000; i++) {
                    manager.isCancellationRequested(session);
                }
            });

            // Start a thread that triggers cancellation
            Thread trigger = new Thread(() -> {
                try {
                    Thread.sleep(5); // Let checker run for a bit
                    manager.triggerCancellation(session);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            checker.start();
            trigger.start();
            checker.join(1000);
            trigger.join(1000);

            // Eventually the flag should be true
            assertThat(flag.get()).isTrue();
        }
    }

    @Nested
    class CompleteWorkflow {

        @Test
        void completeLifecycleCreateCheckTriggerClearWorks() {
            // 1. Create flag
            AtomicBoolean flag = manager.createCancelFlag(session);
            when(session.getAttribute("chatCancelFlag")).thenReturn(flag);

            // 2. Check it's not cancelled initially
            assertThat(manager.isCancellationRequested(session)).isFalse();

            // 3. Trigger cancellation
            boolean triggered = manager.triggerCancellation(session);
            assertThat(triggered).isTrue();

            // 4. Check it's now cancelled
            assertThat(manager.isCancellationRequested(session)).isTrue();

            // 5. Clear the flag
            manager.clearCancelFlag(session);
            verify(session).removeAttribute("chatCancelFlag");
        }
    }
}
