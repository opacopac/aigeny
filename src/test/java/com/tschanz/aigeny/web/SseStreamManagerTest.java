package com.tschanz.aigeny.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm_tool.QueryResult;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.orchestration.ChatResult;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SseStreamManagerTest {

    @Mock
    private SseEmitter emitter;

    @Mock
    private HttpSession session;

    private SseStreamManager manager;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        manager = new SseStreamManager(objectMapper);
    }

    @Nested
    class EmitterCreation {

        @Test
        void createsEmitterWithTimeout() {
            SseEmitter result = manager.createEmitter();

            assertThat(result).isNotNull();
            assertThat(result.getTimeout()).isEqualTo(300_000L); // 5 minutes
        }
    }

    @Nested
    class ToolCallEvents {

        @Test
        void sendsToolCallEvent() throws Exception {
            manager.sendToolCall(emitter, "JiraSearchTool", "Searching for issues");

            // Verify send was called
            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        void handlesToolCallSendFailureGracefully() throws Exception {
            doThrow(new RuntimeException("Network error")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            // Should not throw
            manager.sendToolCall(emitter, "FailingTool", "Will fail");

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    class IntermediateMessages {

        @Test
        void sendsIntermediateMessage() throws Exception {
            manager.sendIntermediateMessage(emitter, "Processing your request...");

            // Verify send was called
            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        void handlesIntermediateSendFailureGracefully() throws Exception {
            doThrow(new RuntimeException("Connection lost")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            // Should not throw
            manager.sendIntermediateMessage(emitter, "This will fail");

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    class CompletionEvents {

        @Test
        void sendsCompletionEventWithoutExport() throws Exception {
            ChatResult result = new ChatResult("Final response", null);

            manager.sendCompletionAndClose(emitter, result);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        void sendsCompletionEventWithExport() throws Exception {
            ToolResult toolResult = mock(ToolResult.class);
            ChatResult result = new ChatResult("Response with export", toolResult);

            manager.sendCompletionAndClose(emitter, result);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        void handlesCompletionSendFailureWithError() throws Exception {
            doThrow(new RuntimeException("Send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
            ChatResult result = new ChatResult("Final response", null);

            manager.sendCompletionAndClose(emitter, result);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).completeWithError(any(RuntimeException.class));
        }
    }

    @Nested
    class CancellationHandling {

        @Test
        void handlesCancellationSuccessfully() throws Exception {
            when(session.getId()).thenReturn("session-123");

            manager.handleCancellation(emitter, session);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        void handlesCancellationSendFailureWithError() throws Exception {
            when(session.getId()).thenReturn("session-123");
            doThrow(new RuntimeException("Send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            manager.handleCancellation(emitter, session);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).completeWithError(any(RuntimeException.class));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void handlesErrorWithMessage() throws Exception {
            Exception error = new RuntimeException("Something went wrong");

            manager.handleError(emitter, error);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        void handlesErrorWithoutMessage() throws Exception {
            Exception error = new NullPointerException(); // No message

            manager.handleError(emitter, error);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        void handlesErrorSendFailureWithCompleteWithError() throws Exception {
            Exception error = new RuntimeException("Original error");
            doThrow(new RuntimeException("Send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            manager.handleError(emitter, error);

            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).completeWithError(any(RuntimeException.class));
        }
    }

    @Nested
    class ValidationErrors {

        @Test
        void sendsValidationErrorAsynchronously() throws Exception {
            manager.sendErrorAndComplete(emitter, "Empty message not allowed");

            verify(emitter, timeout(500)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, timeout(500)).complete();
        }

        @Test
        void handlesValidationErrorSendFailureWithCompleteWithError() throws Exception {
            doThrow(new RuntimeException("Send failed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            manager.sendErrorAndComplete(emitter, "Validation failed");

            verify(emitter, timeout(500)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, timeout(500)).completeWithError(any(RuntimeException.class));
        }
    }
}
