package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
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
    @Mock private SessionCancellationService cancellationService;
    @Mock private SessionExportService exportService;
    @Mock private ConfirmationOrchestrator confirmationOrchestrator;
    @Mock private ExecutionContextManager contextManager;
    @Mock private SseStreamManager sseManager;
    @Mock private HttpSession session;

    private ChatStreamingService streamingService;

    @BeforeEach
    void setUp() {
        streamingService = new ChatStreamingService(orchestration, cancellationService, exportService, confirmationOrchestrator, contextManager, sseManager);

        // Default: sseManager creates a new emitter
        when(sseManager.createEmitter()).thenReturn(new SseEmitter(300_000L));
    }

    @Nested
    @DisplayName("SSE Emitter Creation")
    class SseEmitterCreation {

        @Test
        @DisplayName("should create emitter via SseStreamManager")
        void shouldCreateEmitterViaSseStreamManager() {
            List<Message> history  = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);

            SseEmitter emitter = streamingService.streamChat(
                    "test message", history, session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            verify(sseManager).createEmitter();
        }

        @Test
        @DisplayName("should setup cancel flag on emitter lifecycle events")
        void shouldSetupCancelFlagOnEmitterLifecycleEvents() {
            List<Message> history  = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);
            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);

            streamingService.streamChat("test message", history, session, "token", false, "bb-token");

            verify(cancellationService).createCancelFlag(session);
        }

        @Test
        @DisplayName("should handle empty message with error event via SseStreamManager")
        void shouldHandleEmptyMessageWithErrorEvent() {
            SseEmitter emitter = streamingService.streamChat(
                    "", new ArrayList<>(), session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            verify(sseManager).sendErrorAndComplete(any(SseEmitter.class), anyString());
            verify(cancellationService, never()).createCancelFlag(any());
        }

        @Test
        @DisplayName("should handle null message with error event via SseStreamManager")
        void shouldHandleNullMessageWithErrorEvent() {
            SseEmitter emitter = streamingService.streamChat(
                    null, new ArrayList<>(), session, "token", false, "bb-token");

            assertThat(emitter).isNotNull();
            verify(sseManager).sendErrorAndComplete(any(SseEmitter.class), anyString());
            verify(cancellationService, never()).createCancelFlag(any());
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
            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            streamingService.streamChat("show me data", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(exportService).setLastQueryResult(session, queryResult);
            verify(sseManager).sendCompletionAndClose(any(SseEmitter.class), eq(chatResult));
        }

        @Test
        @DisplayName("should not store export data when not available")
        void shouldNotStoreExportDataWhenNotAvailable() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            ChatResult chatResult = new ChatResult("response", null);
            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any())).thenReturn(chatResult);

            streamingService.streamChat("hello", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(exportService, never()).setLastQueryResult(any(), any());
            verify(sseManager).sendCompletionAndClose(any(SseEmitter.class), eq(chatResult));
        }
    }

    @Nested
    @DisplayName("ThreadLocal Context Management")
    class ThreadLocalContextManagement {

        @Test
        @DisplayName("should setup and cleanup contexts via ExecutionContextManager")
        void shouldSetupAndCleanupContextsViaExecutionContextManager() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenReturn(new ChatResult("response", null));

            streamingService.streamChat("test", history, session, "jira-token", true, "bb-token");
            Thread.sleep(100);

            verify(contextManager).setupContexts(
                    argThat(tokens -> "jira-token".equals(tokens.get(JiraContextProvider.KEY))
                                   && "bb-token".equals(tokens.get(BitbucketContextProvider.KEY))),
                    eq(true),
                    any(),
                    any()
            );
            verify(contextManager).cleanupAllContexts();
        }

        @Test
        @DisplayName("should cleanup contexts even after error")
        void shouldCleanupContextsEvenAfterError() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            streamingService.streamChat("test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(contextManager).cleanupAllContexts();
        }
    }

    @Nested
    @DisplayName("Cancellation Handling")
    class CancellationHandling {

        @Test
        @DisplayName("should handle InterruptedException via SseStreamManager")
        void shouldHandleInterruptedExceptionAsCancellation() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new InterruptedException("Cancelled"));

            streamingService.streamChat("test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            verify(sseManager).handleCancellation(any(SseEmitter.class), eq(session));
            verify(cancellationService).clearCancelFlag(session);
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

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
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

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
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
        @DisplayName("should handle generic exceptions via SseStreamManager")
        void shouldHandleGenericExceptionsGracefully() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Test error"));

            SseEmitter emitter = streamingService.streamChat(
                    "test", history, session, "token", false, "bb-token");
            Thread.sleep(100);

            assertThat(emitter).isNotNull();
            verify(sseManager).handleError(any(SseEmitter.class), any(RuntimeException.class));
            verify(cancellationService).clearCancelFlag(session);
        }

        @Test
        @DisplayName("should cleanup even when orchestration throws exception")
        void shouldCleanupEvenWhenOrchestrationThrowsException() throws Exception {
            List<Message> history = new ArrayList<>();
            AtomicBoolean cancelFlag = new AtomicBoolean(false);

            when(cancellationService.createCancelFlag(session)).thenReturn(cancelFlag);
            when(orchestration.chat(any(), anyString(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Orchestration failed"));

            streamingService.streamChat("test", history, session, "token", true, "bb-token");
            Thread.sleep(100);

            verify(cancellationService).clearCancelFlag(session);
        }
    }
}
