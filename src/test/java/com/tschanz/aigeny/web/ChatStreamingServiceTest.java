package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteExecutor;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatStreamingService")
class ChatStreamingServiceTest {

    @Mock private OrchestrationService orchestration;
    @Mock private ChatSessionService sessionService;
    @Mock private JiraWriteExecutor jiraWriteExecutor;
    @Mock private HttpSession session;

    private ObjectMapper objectMapper;
    private ChatStreamingService streamingService;

    @BeforeEach
    void setUp() {
        objectMapper    = new ObjectMapper();
        streamingService = new ChatStreamingService(orchestration, sessionService, jiraWriteExecutor, objectMapper);
    }

    @Nested
    @DisplayName("SSE Emitter Creation")
    class SseEmitterCreation {

        @Test
        @DisplayName("should create emitter with 5-minute timeout")
        void shouldCreateEmitterWithTimeout() {
            List<Message> history  = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);

            SseEmitter emitter = streamingService.streamChat(
                    "test message", history, session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            assertThat(emitter.getTimeout()).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("should setup cancel flag on emitter lifecycle events")
        void shouldSetupCancelFlagOnEmitterLifecycleEvents() {
            List<Message> history  = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);

            streamingService.streamChat("test message", history, session, "token", false, "bb-token");

            verify(sessionService).createCancelFlag(session);
        }

        @Test
        @DisplayName("should handle empty message with error event")
        void shouldHandleEmptyMessageWithErrorEvent() {
            SseEmitter emitter = streamingService.streamChat(
                    "", new ArrayList<>(), session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            verify(sessionService, never()).createCancelFlag(any());
        }

        @Test
        @DisplayName("should handle null message with error event")
        void shouldHandleNullMessageWithErrorEvent() {
            SseEmitter emitter = streamingService.streamChat(
                    null, new ArrayList<>(), session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            verify(sessionService, never()).createCancelFlag(any());
        }
    }

    @Nested
    @DisplayName("Chat Result Processing")
    class ChatResultProcessing {

        @Test
        @DisplayName("should store export data when available")
        void shouldStoreExportDataWhenAvailable() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            QueryResult queryResult = mock(QueryResult.class);
            ToolResult toolResult   = mock(ToolResult.class);
            when(toolResult.getQueryResult()).thenReturn(queryResult);
            when(toolResult.hasQueryResult()).thenReturn(true);

            ChatResult chatResult = new ChatResult("response", toolResult);
            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            streamingService.streamChat("show me data", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(sessionService).setLastQueryResult(session, queryResult);
        }

        @Test
        @DisplayName("should not store export data when not available")
        void shouldNotStoreExportDataWhenNotAvailable() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenReturn(new ChatResult("response", null));

            streamingService.streamChat("hello", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(sessionService, never()).setLastQueryResult(any(), any());
        }
    }

    @Nested
    @DisplayName("ThreadLocal Context Management")
    class ThreadLocalContextManagement {

        @Test
        @DisplayName("should cleanup ThreadLocal contexts after completion")
        void shouldCleanupThreadLocalContextsAfterCompletion() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenReturn(new ChatResult("response", null));

            streamingService.streamChat("test", history, session, "token", true, "bb-token");
            Thread.sleep(100);

            verify(sessionService).clearCancelFlag(session);
        }

        @Test
        @DisplayName("should cleanup ThreadLocal contexts after error")
        void shouldCleanupThreadLocalContextsAfterError() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            streamingService.streamChat("test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(sessionService).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Cancellation Handling")
    class CancellationHandling {

        @Test
        @DisplayName("should handle InterruptedException as cancellation")
        void shouldHandleInterruptedExceptionAsCancellation() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new InterruptedException("Cancelled"));
            when(session.getId()).thenReturn("session-123");

            streamingService.streamChat("test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(sessionService).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Orchestration Integration")
    class OrchestrationIntegration {

        @Test
        @DisplayName("should pass callbacks to orchestration service")
        void shouldPassCallbacksToOrchestrationService() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenReturn(new ChatResult("response", null));

            streamingService.streamChat("test message", history, session, "jira-token", true, "bb-token");
            Thread.sleep(100);

            ArgumentCaptor<BiConsumer<String, String>> toolCallCaptor   = ArgumentCaptor.forClass(BiConsumer.class);
            ArgumentCaptor<Consumer<String>>          intermediateCaptor = ArgumentCaptor.forClass(Consumer.class);
            ArgumentCaptor<Supplier<Boolean>>         cancelSupplierCaptor = ArgumentCaptor.forClass(Supplier.class);

            verify(orchestration).chat(
                    eq(history),
                    eq("test message"),
                    toolCallCaptor.capture(),
                    intermediateCaptor.capture(),
                    cancelSupplierCaptor.capture()
            );

            assertThat(toolCallCaptor.getValue()).isNotNull();
            assertThat(intermediateCaptor.getValue()).isNotNull();
            assertThat(cancelSupplierCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("should pass correct message to orchestration")
        void shouldPassCorrectMessageToOrchestration() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenReturn(new ChatResult("response", null));

            streamingService.streamChat("my test message", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(orchestration).chat(eq(history), eq("my test message"), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle generic exceptions gracefully")
        void shouldHandleGenericExceptionsGracefully() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            SseEmitter emitter = streamingService.streamChat(
                    "test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            assertThat(emitter).isNotNull();
            verify(sessionService).clearCancelFlag(session);
        }

        @Test
        @DisplayName("should cleanup even when orchestration throws exception")
        void shouldCleanupEvenWhenOrchestrationThrowsException() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Orchestration failed"));

            streamingService.streamChat("test", history, session, "token", true, "bb-token");
            Thread.sleep(100);

            verify(sessionService).clearCancelFlag(session);
        }
    }
}

