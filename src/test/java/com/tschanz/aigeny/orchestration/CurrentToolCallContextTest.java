package com.tschanz.aigeny.orchestration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CurrentToolCallContext")
class CurrentToolCallContextTest {

    @AfterEach
    void tearDown() {
        CurrentToolCallContext.clear();
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should return null when nothing is set")
        void shouldReturnNullWhenNothingSet() {
            assertThat(CurrentToolCallContext.get()).isNull();
        }

        @Test
        @DisplayName("should store and retrieve tool call ID")
        void shouldStoreAndRetrieveId() {
            CurrentToolCallContext.set("call-abc-123");
            assertThat(CurrentToolCallContext.get()).isEqualTo("call-abc-123");
        }

        @Test
        @DisplayName("should clear stored ID")
        void shouldClearStoredId() {
            CurrentToolCallContext.set("call-xyz");
            CurrentToolCallContext.clear();
            assertThat(CurrentToolCallContext.get()).isNull();
        }

        @Test
        @DisplayName("should overwrite previous ID on second set")
        void shouldOverwritePreviousId() {
            CurrentToolCallContext.set("call-1");
            CurrentToolCallContext.set("call-2");
            assertThat(CurrentToolCallContext.get()).isEqualTo("call-2");
        }
    }

    @Nested
    @DisplayName("Thread isolation")
    class ThreadIsolation {

        @Test
        @DisplayName("ID set in main thread should not be visible in another thread")
        void idShouldBeThreadLocal() throws InterruptedException {
            CurrentToolCallContext.set("main-thread-id");

            AtomicReference<String> capturedFromOtherThread = new AtomicReference<>();
            Thread t = new Thread(() ->
                    capturedFromOtherThread.set(CurrentToolCallContext.get()));
            t.start();
            t.join();

            assertThat(capturedFromOtherThread.get()).isNull();
        }
    }
}
