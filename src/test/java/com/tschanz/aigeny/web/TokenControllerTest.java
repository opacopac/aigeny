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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link TokenController}.
 *
 * <p>Covers:
 * <ul>
 *   <li>POST   /api/jira/token      – set or clear Jira token</li>
 *   <li>DELETE /api/jira/token      – explicit Jira token removal</li>
 *   <li>POST   /api/jira/write-mode – enable / disable Jira write mode</li>
 *   <li>POST   /api/bitbucket/token – set or clear Bitbucket token</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenController")
class TokenControllerTest {

    @Mock private TokenService tokenService;
    @Mock private SessionJiraWriteService jiraWriteService;
    @Mock private HttpSession session;

    private TokenController controller;

    @BeforeEach
    void setUp() {
        controller = new TokenController(tokenService, jiraWriteService);
    }

    // ── POST /api/jira/token ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/jira/token")
    class SetJiraToken {

        @Test
        @DisplayName("delegates to TokenService and returns status=ok for non-empty token")
        void setsTokenAndReturnsOk() {
            ResponseEntity<Map<String, String>> response =
                    controller.setJiraToken(Map.of("token", "secret"), session);

            verify(tokenService).setUserJiraToken(session, "secret");
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "ok");
        }

        @Test
        @DisplayName("returns cleared status for empty token")
        void emptyTokenReturnsClearedStatus() {
            ResponseEntity<Map<String, String>> response =
                    controller.setJiraToken(Map.of("token", ""), session);

            verify(tokenService).setUserJiraToken(session, "");
            assertThat(response.getBody().get("status")).isNotNull();
            // Message is loaded from messages.properties; just verify it is present
        }

        @Test
        @DisplayName("strips whitespace before delegating")
        void stripsWhitespace() {
            controller.setJiraToken(Map.of("token", "  tok  "), session);

            verify(tokenService).setUserJiraToken(session, "tok");
        }
    }

    // ── DELETE /api/jira/token ────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/jira/token")
    class ClearJiraToken {

        @Test
        @DisplayName("delegates to TokenService.clearUserJiraToken and returns cleared status")
        void clearsToken() {
            ResponseEntity<Map<String, String>> response = controller.clearJiraToken(session);

            verify(tokenService).clearUserJiraToken(session);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsKey("status");
            verifyNoMoreInteractions(jiraWriteService);
        }
    }

    // ── POST /api/jira/write-mode ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/jira/write-mode")
    class SetJiraWriteMode {

        @Test
        @DisplayName("enables write mode and returns status=ok")
        void enablesWriteMode() {
            ResponseEntity<Map<String, String>> response =
                    controller.setJiraWriteMode(Map.of("enabled", "true"), session);

            verify(jiraWriteService).setJiraWriteMode(session, true);
            assertThat(response.getBody()).containsEntry("status", "ok");
        }

        @Test
        @DisplayName("disables write mode when enabled=false")
        void disablesWriteMode() {
            controller.setJiraWriteMode(Map.of("enabled", "false"), session);

            verify(jiraWriteService).setJiraWriteMode(session, false);
        }

        @Test
        @DisplayName("defaults to false when key is missing")
        void defaultsToFalse() {
            controller.setJiraWriteMode(Map.of(), session);

            verify(jiraWriteService).setJiraWriteMode(session, false);
        }
    }

    // ── POST /api/bitbucket/token ─────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/bitbucket/token")
    class SetBitbucketToken {

        @Test
        @DisplayName("delegates to TokenService and returns status=ok for non-empty token")
        void setsTokenAndReturnsOk() {
            ResponseEntity<Map<String, String>> response =
                    controller.setBitbucketToken(Map.of("token", "bb-secret"), session);

            verify(tokenService).setUserBitbucketToken(session, "bb-secret");
            assertThat(response.getBody()).containsEntry("status", "ok");
        }

        @Test
        @DisplayName("returns cleared status for empty token")
        void emptyTokenReturnsClearedStatus() {
            ResponseEntity<Map<String, String>> response =
                    controller.setBitbucketToken(Map.of("token", ""), session);

            verify(tokenService).setUserBitbucketToken(session, "");
            assertThat(response.getBody()).containsKey("status");
        }

        @Test
        @DisplayName("strips whitespace before delegating")
        void stripsWhitespace() {
            controller.setBitbucketToken(Map.of("token", "  bb  "), session);

            verify(tokenService).setUserBitbucketToken(session, "bb");
        }
    }
}
