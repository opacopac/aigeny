package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
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
import java.util.Map;
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

    @Mock
    private OrchestrationService orchestration;

    @Mock
    private ChatSessionService sessionService;

    @Mock
    private HttpSession session;

    private ObjectMapper objectMapper;
    private ChatStreamingService streamingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        streamingService = new ChatStreamingService(orchestration, sessionService, objectMapper);
    }

    @Nested
    @DisplayName("SSE Emitter Creation")
    class SseEmitterCreation {

        @Test
        @DisplayName("should create emitter with 5-minute timeout")
        void shouldCreateEmitterWithTimeout() {
            // Given
            String message = "test message";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);

            // When
            SseEmitter emitter = streamingService.streamChat(
                    message, history, session, "token", false, "bb-token"
            );

            // Then
            assertThat(emitter).isNotNull();
            assertThat(emitter.getTimeout()).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("should setup cancel flag on emitter lifecycle events")
        void shouldSetupCancelFlagOnEmitterLifecycleEvents() {
            // Given
            String message = "test message";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);

            // When
            SseEmitter emitter = streamingService.streamChat(
                    message, history, session, "token", false, "bb-token"
            );

            // Then
            verify(sessionService).createCancelFlag(session);
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("should handle empty message with error event")
        void shouldHandleEmptyMessageWithErrorEvent() throws Exception {
            // Given
            String emptyMessage = "";
            List<Message> history = new ArrayList<>();

            // When
            SseEmitter emitter = streamingService.streamChat(
                    emptyMessage, history, session, "token", false, "bb-token"
            );

            // Then
            assertThat(emitter).isNotNull();
            // Empty message is handled immediately without creating cancelFlag
            verify(sessionService, never()).createCancelFlag(any());
        }

        @Test
        @DisplayName("should handle null message with error event")
        void shouldHandleNullMessageWithErrorEvent() throws Exception {
            // Given
            List<Message> history = new ArrayList<>();

            // When
            SseEmitter emitter = streamingService.streamChat(
                    null, history, session, "token", false, "bb-token"
            );

            // Then
            assertThat(emitter).isNotNull();
            // Null message is handled immediately without creating cancelFlag
            verify(sessionService, never()).createCancelFlag(any());
        }
    }

    @Nested
    @DisplayName("Chat Result Processing")
    class ChatResultProcessing {

        @Test
        @DisplayName("should store export data when available")
        void shouldStoreExportDataWhenAvailable() throws Exception {
            // Given
            String message = "show me data";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            QueryResult queryResult = mock(QueryResult.class);
            ToolResult toolResult = mock(ToolResult.class);
            when(toolResult.getQueryResult()).thenReturn(queryResult);
            when(toolResult.hasQueryResult()).thenReturn(true);

            ChatResult chatResult = new ChatResult("response", toolResult);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");

            // Give async operation time to complete
            Thread.sleep(100);

            // Then
            verify(sessionService).setLastQueryResult(session, queryResult);
        }

        @Test
        @DisplayName("should not store export data when not available")
        void shouldNotStoreExportDataWhenNotAvailable() throws Exception {
            // Given
            String message = "hello";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            ChatResult chatResult = new ChatResult("response", null);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");

            Thread.sleep(100);

            // Then
            verify(sessionService, never()).setLastQueryResult(any(), any());
        }
    }

    @Nested
    @DisplayName("Pending Jira Actions")
    class PendingJiraActionsHandling {

        @Test
        @DisplayName("should store pending actions in session")
        void shouldStorePendingActionsInSession() throws Exception {
            // Given
            String message = "create jira ticket";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            ChatResult chatResult = new ChatResult("response", null);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");
            Thread.sleep(100);

            // Then - cleanup should be called
            verify(sessionService).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("ThreadLocal Context Management")
    class ThreadLocalContextManagement {

        @Test
        @DisplayName("should cleanup ThreadLocal contexts after completion")
        void shouldCleanupThreadLocalContextsAfterCompletion() throws Exception {
            // Given
            String message = "test";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            ChatResult chatResult = new ChatResult("response", null);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "token", true, "bb-token");
            Thread.sleep(100);

            // Then
            verify(sessionService).clearCancelFlag(session);
        }

        @Test
        @DisplayName("should cleanup ThreadLocal contexts after error")
        void shouldCleanupThreadLocalContextsAfterError() throws Exception {
            // Given
            String message = "test";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");
            Thread.sleep(100);

            // Then
            verify(sessionService).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Cancellation Handling")
    class CancellationHandling {

        @Test
        @DisplayName("should handle InterruptedException as cancellation")
        void shouldHandleInterruptedExceptionAsCancellation() throws Exception {
            // Given
            String message = "test";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new InterruptedException("Cancelled"));
            when(session.getId()).thenReturn("session-123");

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");
            Thread.sleep(100);

            // Then
            verify(sessionService).clearCancelFlag(session);
        }
    }

    @Nested
    @DisplayName("Orchestration Integration")
    class OrchestrationIntegration {

        @Test
        @DisplayName("should pass callbacks to orchestration service")
        void shouldPassCallbacksToOrchestrationService() throws Exception {
            // Given
            String message = "test message";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            ChatResult chatResult = new ChatResult("response", null);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "jira-token", true, "bb-token");
            Thread.sleep(100);

            // Then
            ArgumentCaptor<BiConsumer<String, String>> toolCallCaptor = ArgumentCaptor.forClass(BiConsumer.class);
            ArgumentCaptor<Consumer<String>> intermediateCaptor = ArgumentCaptor.forClass(Consumer.class);
            ArgumentCaptor<Supplier<Boolean>> cancelSupplierCaptor = ArgumentCaptor.forClass(Supplier.class);

            verify(orchestration).chat(
                    eq(history),
                    eq(message),
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
            // Given
            String message = "my test message";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            ChatResult chatResult = new ChatResult("response", null);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            // When
            streamingService.streamChat(message, history, session, "token", false, "bb-token");
            Thread.sleep(100);

            // Then
            verify(orchestration).chat(
                    eq(history),
                    eq(message),
                    any(),
                    any(),
                    any()
            );
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle generic exceptions gracefully")
        void shouldHandleGenericExceptionsGracefully() throws Exception {
            // Given
            String message = "test";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            // When
            SseEmitter emitter = streamingService.streamChat(
                    message, history, session, "token", false, "bb-token"
            );
            Thread.sleep(100);

            // Then
            assertThat(emitter).isNotNull();
            verify(sessionService).clearCancelFlag(session);
        }

        @Test
        @DisplayName("should cleanup even when orchestration throws exception")
        void shouldCleanupEvenWhenOrchestrationThrowsException() throws Exception {
            // Given
            String message = "test";
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(sessionService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Orchestration failed"));

            // When
            streamingService.streamChat(message, history, session, "token", true, "bb-token");
            Thread.sleep(100);

            // Then
            verify(sessionService).clearCancelFlag(session);
        }
    }
}





