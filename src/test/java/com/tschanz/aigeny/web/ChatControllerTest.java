package com.tschanz.aigeny.web;

import com.tschanz.aigeny.db.SchemaLoader;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.orchestration.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatController}.
 *
 * <p>Focuses on D-3: the non-streaming {@code POST /api/chat} path must delegate
 * ThreadLocal context setup/cleanup to {@link ExecutionContextManager} instead of
 * calling the static context classes directly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController")
class ChatControllerTest {
    @Mock private OrchestrationService orchestration;
    @Mock private SchemaLoader schemaLoader;
    @Mock private ObjectMapper objectMapper;
    @Mock private TokenService tokenService;
    @Mock private ChatSessionService sessionService;
    @Mock private StatusAggregatorService statusAggregator;
    @Mock private ChatStreamingService streamingService;
    @Mock private ExecutionContextManager contextManager;
    @Mock private HttpSession session;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(
                orchestration, schemaLoader, objectMapper,
                tokenService, sessionService, statusAggregator,
                streamingService, contextManager);
    }

    // ── POST /api/chat (non-streaming) – D-3 ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/chat – ExecutionContextManager usage (D-3)")
    class NonStreamingChatContextManagement {

        private List<Message> history;

        @BeforeEach
        void setUp() throws Exception {
            history = new ArrayList<>();
            // Use lenient() so the test for empty message (which doesn't reach these mocks)
            // does not trigger UnnecessaryStubbingException
            lenient().when(sessionService.getOrCreateHistory(session)).thenReturn(history);
            lenient().when(tokenService.getEffectiveJiraToken(session)).thenReturn("jira-token");
            lenient().when(sessionService.isJiraWriteModeEnabled(session)).thenReturn(false);
            lenient().when(tokenService.getEffectiveBitbucketToken(session)).thenReturn("bb-token");
            lenient().when(orchestration.chat(any(), anyString()))
                    .thenReturn(new ChatResult("response text", null));
        }

        @Test
        @DisplayName("calls setupContexts with tokens from session before orchestration")
        void callsSetupContextsBeforeOrchestration() throws Exception {
            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).setupContexts(
                    eq("jira-token"),
                    eq(false),
                    eq("bb-token"),
                    isNull(),   // no confirmation handler in non-streaming path
                    isNull()    // no batch handler in non-streaming path
            );
        }

        @Test
        @DisplayName("calls cleanupAllContexts after successful orchestration")
        void callsCleanupAfterSuccess() throws Exception {
            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).cleanupAllContexts();
        }

        @Test
        @DisplayName("calls cleanupAllContexts even when orchestration throws an exception")
        void callsCleanupAfterException() throws Exception {
            when(orchestration.chat(any(), anyString()))
                    .thenThrow(new RuntimeException("LLM failure"));

            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).cleanupAllContexts();
        }

        @Test
        @DisplayName("calls setupContexts with jiraWriteEnabled=true when session has write mode on")
        void callsSetupContextsWithWriteModeEnabled() throws Exception {
            when(sessionService.isJiraWriteModeEnabled(session)).thenReturn(true);

            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).setupContexts(
                    anyString(), eq(true), anyString(), isNull(), isNull());
        }

        @Test
        @DisplayName("returns bad request for empty message (no context setup needed)")
        void returnsBadRequestForEmptyMessage() throws Exception {
            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", ""), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(contextManager);
        }

        @Test
        @DisplayName("returns ok response containing the orchestration result text")
        void returnsOkWithResponseText() throws Exception {
            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", "hello"), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("response");
            assertThat(response.getBody().get("response")).isEqualTo("response text");
        }

        @Test
        @DisplayName("returns error response (not exception) when orchestration throws")
        void returnsErrorResponseOnException() throws Exception {
            when(orchestration.chat(any(), anyString()))
                    .thenThrow(new RuntimeException("oops"));

            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", "hello"), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("response");
        }
    }
}



