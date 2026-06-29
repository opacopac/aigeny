package com.tschanz.aigeny.web;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfirmationController}.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST /api/jira/confirm-decision – single confirmation (confirm / decline / no-pending)</li>
 *   <li>POST /api/jira/batch-confirm-decision – batch confirmation (decisions map / confirmAll shortcut / no-pending)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmationController")
class ConfirmationControllerTest {

    @Mock private ChatSessionService sessionService;
    @Mock private HttpSession session;

    private ConfirmationController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfirmationController(sessionService);
    }

    // ── POST /api/jira/confirm-decision ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/jira/confirm-decision")
    class SingleConfirmation {

        @Test
        @DisplayName("returns status=ok when user confirms and future resolves")
        void confirmedReturnsOk() {
            when(session.getId()).thenReturn("s1");
            when(sessionService.resolveConfirmation(session, true)).thenReturn(true);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraConfirmDecision(Map.of("confirmed", "true"), session);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "ok");
            verify(sessionService).resolveConfirmation(session, true);
        }

        @Test
        @DisplayName("returns status=ok when user declines and future resolves")
        void declinedReturnsOk() {
            when(session.getId()).thenReturn("s1");
            when(sessionService.resolveConfirmation(session, false)).thenReturn(true);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraConfirmDecision(Map.of("confirmed", "false"), session);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "ok");
        }

        @Test
        @DisplayName("defaults confirmed=false when body key is absent")
        void defaultsToDeclined() {
            when(session.getId()).thenReturn("s1");
            when(sessionService.resolveConfirmation(session, false)).thenReturn(true);

            controller.jiraConfirmDecision(Map.of(), session);

            verify(sessionService).resolveConfirmation(session, false);
        }

        @Test
        @DisplayName("returns status=no_pending when there is no pending confirmation")
        void noPendingReturnsNoPending() {
            when(session.getId()).thenReturn("s1");
            when(sessionService.resolveConfirmation(session, true)).thenReturn(false);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraConfirmDecision(Map.of("confirmed", "true"), session);

            assertThat(response.getBody()).containsEntry("status", "no_pending");
        }
    }

    // ── POST /api/jira/batch-confirm-decision ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/jira/batch-confirm-decision")
    class BatchConfirmation {

        @Test
        @DisplayName("resolves with per-tool-call decisions map and returns status=ok")
        void decisionsMapReturnsOk() {
            when(session.getId()).thenReturn("s1");
            Map<String, Boolean> decisions = Map.of("tc1", true, "tc2", false);
            when(sessionService.resolveBatchConfirmation(session, decisions)).thenReturn(true);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraBatchConfirmDecision(Map.of("decisions", decisions), session);

            assertThat(response.getBody()).containsEntry("status", "ok");
            verify(sessionService).resolveBatchConfirmation(session, decisions);
        }

        @Test
        @DisplayName("resolves with __confirmAll__ sentinel when confirmAll=true shortcut is used")
        void confirmAllTrueUsesSentinel() {
            when(session.getId()).thenReturn("s1");
            Map<String, Boolean> sentinel = Map.of("__confirmAll__", true);
            when(sessionService.resolveBatchConfirmation(session, sentinel)).thenReturn(true);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraBatchConfirmDecision(Map.of("confirmAll", "true"), session);

            assertThat(response.getBody()).containsEntry("status", "ok");
            verify(sessionService).resolveBatchConfirmation(session, sentinel);
        }

        @Test
        @DisplayName("resolves with __confirmAll__=false sentinel when confirmAll=false shortcut is used")
        void confirmAllFalseUsesSentinel() {
            when(session.getId()).thenReturn("s1");
            Map<String, Boolean> sentinel = Map.of("__confirmAll__", false);
            when(sessionService.resolveBatchConfirmation(session, sentinel)).thenReturn(true);

            controller.jiraBatchConfirmDecision(Map.of("confirmAll", "false"), session);

            verify(sessionService).resolveBatchConfirmation(session, sentinel);
        }

        @Test
        @DisplayName("returns status=no_pending when there is no pending batch future")
        void noPendingReturnsNoPending() {
            when(session.getId()).thenReturn("s1");
            Map<String, Boolean> decisions = Map.of("tc1", true);
            when(sessionService.resolveBatchConfirmation(any(), any())).thenReturn(false);

            ResponseEntity<Map<String, String>> response =
                    controller.jiraBatchConfirmDecision(Map.of("decisions", decisions), session);

            assertThat(response.getBody()).containsEntry("status", "no_pending");
        }
    }
}

