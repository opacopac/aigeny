package com.tschanz.aigeny.chat;
import com.tschanz.aigeny.jira.JiraContextProvider;
import com.tschanz.aigeny.bitbucket.BitbucketContextProvider;
import com.tschanz.aigeny.llm.github.TokenService;
import com.tschanz.aigeny.export.SessionExportService;
import com.tschanz.aigeny.session.SessionCancellationService;
import com.tschanz.aigeny.jira.SessionJiraWriteService;
import com.tschanz.aigeny.confirmation.ExecutionContextManager;

import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.chat.ChatResult;
import com.tschanz.aigeny.orchestration.OrchestrationService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChatController}.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/chat – non-streaming path including D-3 (ExecutionContextManager usage)</li>
 *   <li>POST /api/chat/stream – delegates to ChatStreamingService</li>
 *   <li>POST /api/chat/cancel – delegates cancellation to ChatSessionService</li>
 *   <li>POST /api/chat/clear  – clears history and export data</li>
 *   <li>GET  /api/status      – delegates to StatusAggregatorService</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Mock private OrchestrationService orchestration;
    @Mock private TokenService tokenService;
    @Mock private SessionHistoryService historyService;
    @Mock private SessionExportService exportService;
    @Mock private SessionCancellationService cancellationService;
    @Mock private SessionJiraWriteService jiraWriteService;
    @Mock private StatusAggregatorService statusAggregator;
    @Mock private ChatStreamingService streamingService;
    @Mock private ExecutionContextManager contextManager;
    @Mock private HttpSession session;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(
                orchestration, tokenService,
                historyService, exportService, cancellationService, jiraWriteService,
                statusAggregator, streamingService, contextManager);
    }

    // ── POST /api/chat (non-streaming) ────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/chat")
    class NonStreamingChat {

        private List<Message> history;

        @BeforeEach
        void setUp() throws Exception {
            history = new ArrayList<>();
            lenient().when(historyService.getOrCreateHistory(session)).thenReturn(history);
            lenient().when(tokenService.getEffectiveJiraToken(session)).thenReturn("jira-token");
            lenient().when(jiraWriteService.isJiraWriteModeEnabled(session)).thenReturn(false);
            lenient().when(tokenService.getEffectiveBitbucketToken(session)).thenReturn("bb-token");
            lenient().when(orchestration.chat(any(), anyString()))
                    .thenReturn(new ChatResult("response text", null));
        }

        @Test
        @DisplayName("calls setupContexts with tokens from session before orchestration (D-3)")
        void callsSetupContextsBeforeOrchestration() throws Exception {
            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).setupContexts(
                    argThat(tokens -> "jira-token".equals(tokens.get(JiraContextProvider.KEY))
                                   && "bb-token".equals(tokens.get(BitbucketContextProvider.KEY))),
                    eq(false),
                    isNull(),   // no confirmation handler in non-streaming path
                    isNull()    // no batch handler in non-streaming path
            );
        }

        @Test
        @DisplayName("calls cleanupAllContexts after successful orchestration (D-3)")
        void callsCleanupAfterSuccess() throws Exception {
            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).cleanupAllContexts();
        }

        @Test
        @DisplayName("calls cleanupAllContexts even when orchestration throws (D-3)")
        void callsCleanupAfterException() throws Exception {
            when(orchestration.chat(any(), anyString()))
                    .thenThrow(new RuntimeException("LLM failure"));

            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).cleanupAllContexts();
        }

        @Test
        @DisplayName("passes jiraWriteEnabled=true when session write-mode is on")
        void passesWriteModeEnabled() throws Exception {
            when(jiraWriteService.isJiraWriteModeEnabled(session)).thenReturn(true);

            controller.chat(Map.of("message", "hello"), session).get();

            verify(contextManager).setupContexts(
                    anyMap(), eq(true), isNull(), isNull());
        }

        @Test
        @DisplayName("returns 400 for empty message – no context setup called")
        void returnsBadRequestForEmptyMessage() throws Exception {
            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", ""), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            verifyNoInteractions(contextManager);
        }

        @Test
        @DisplayName("returns 200 with response text on success")
        void returnsOkWithResponseText() throws Exception {
            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", "hello"), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("response");
            assertThat(response.getBody().get("response")).isEqualTo("response text");
        }

        @Test
        @DisplayName("returns 200 error-response (not exception) when orchestration throws")
        void returnsErrorResponseOnException() throws Exception {
            when(orchestration.chat(any(), anyString()))
                    .thenThrow(new RuntimeException("oops"));

            ResponseEntity<Map<String, Object>> response =
                    controller.chat(Map.of("message", "hello"), session).get();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("response");
        }
    }

    // ── POST /api/chat/stream ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/chat/stream")
    class StreamingChat {

        @Test
        @DisplayName("delegates to ChatStreamingService with tokens resolved from session")
        void delegatesToStreamingService() {
            List<Message> history = new ArrayList<>();
            SseEmitter emitter = new SseEmitter();

            when(historyService.getOrCreateHistory(session)).thenReturn(history);
            when(tokenService.getEffectiveJiraToken(session)).thenReturn("jira-tok");
            when(jiraWriteService.isJiraWriteModeEnabled(session)).thenReturn(true);
            when(tokenService.getEffectiveBitbucketToken(session)).thenReturn("bb-tok");
            when(streamingService.streamChat(
                    eq("hi"), same(history), same(session),
                    eq("jira-tok"), eq(true), eq("bb-tok")))
                    .thenReturn(emitter);

            SseEmitter result = controller.chatStream(Map.of("message", "hi"), session);

            assertThat(result).isSameAs(emitter);
        }
    }

    // ── POST /api/chat/cancel ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/chat/cancel")
    class CancelChat {

        @Test
        @DisplayName("triggers cancellation on the session and returns status ok")
        void triggersCancellation() {
            when(session.getId()).thenReturn("sess-1");

            ResponseEntity<Map<String, String>> response = controller.cancelChat(session);

            verify(cancellationService).triggerCancellation(session);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "ok");
        }
    }

    // ── POST /api/chat/clear ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/chat/clear")
    class ClearChat {

        @Test
        @DisplayName("clears chat history and last query result")
        void clearsHistoryAndExportData() {
            ResponseEntity<Map<String, String>> response = controller.clear(session);

            verify(historyService).clearHistory(session);
            verify(exportService).clearLastQueryResult(session);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("status");
        }
    }

    // ── GET /api/status ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/status")
    class GetStatus {

        @Test
        @DisplayName("returns status map from StatusAggregatorService")
        void returnsAggregatedStatus() {
            Map<String, Object> statusMap = Map.of("llmProvider", "claude");
            when(statusAggregator.aggregateStatus(session)).thenReturn(statusMap);

            ResponseEntity<Map<String, Object>> response = controller.status(session);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("llmProvider", "claude");
        }
    }
}
