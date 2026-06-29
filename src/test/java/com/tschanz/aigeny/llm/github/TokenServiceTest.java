package com.tschanz.aigeny.llm.github;

import com.tschanz.aigeny.config.AigenyProperties;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService")
class TokenServiceTest {

    private AigenyProperties.Jira jiraConfig;
    private AigenyProperties.Bitbucket bitbucketConfig;

    @Mock
    private HttpSession session;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        jiraConfig = new AigenyProperties.Jira();
        bitbucketConfig = new AigenyProperties.Bitbucket();
        tokenService = new TokenService(jiraConfig, bitbucketConfig);
    }

    @Nested
    @DisplayName("Jira Token Resolution")
    class JiraTokenResolution {

        @Test
        @DisplayName("should return user token when set in session")
        void shouldReturnUserTokenWhenSetInSession() {
            // Given
            String userToken = "user-jira-token";
            String configToken = "config-jira-token";
            when(session.getAttribute("jiraToken")).thenReturn(userToken);
            jiraConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveJiraToken(session);

            // Then
            assertThat(result).isEqualTo(userToken);
        }

        @Test
        @DisplayName("should return config token when no user token in session")
        void shouldReturnConfigTokenWhenNoUserToken() {
            // Given
            String configToken = "config-jira-token";
            when(session.getAttribute("jiraToken")).thenReturn(null);
            jiraConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveJiraToken(session);

            // Then
            assertThat(result).isEqualTo(configToken);
        }

        @Test
        @DisplayName("should return config token when user token is blank")
        void shouldReturnConfigTokenWhenUserTokenIsBlank() {
            // Given
            String configToken = "config-jira-token";
            when(session.getAttribute("jiraToken")).thenReturn("   ");
            jiraConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveJiraToken(session);

            // Then
            assertThat(result).isEqualTo(configToken);
        }

        @Test
        @DisplayName("should return null when session is null and no config token")
        void shouldReturnNullWhenSessionIsNullAndNoConfigToken() {
            // Given
            jiraConfig.setToken(null);

            // When
            String result = tokenService.getEffectiveJiraToken(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return config token when session is null but config is set")
        void shouldReturnConfigTokenWhenSessionIsNull() {
            // Given
            String configToken = "config-jira-token";
            jiraConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveJiraToken(null);

            // Then
            assertThat(result).isEqualTo(configToken);
        }
    }

    @Nested
    @DisplayName("Bitbucket Token Resolution")
    class BitbucketTokenResolution {

        @Test
        @DisplayName("should return user token when set in session")
        void shouldReturnUserTokenWhenSetInSession() {
            // Given
            String userToken = "user-bitbucket-token";
            String configToken = "config-bitbucket-token";
            when(session.getAttribute("bitbucketToken")).thenReturn(userToken);
            bitbucketConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveBitbucketToken(session);

            // Then
            assertThat(result).isEqualTo(userToken);
        }

        @Test
        @DisplayName("should return config token when no user token in session")
        void shouldReturnConfigTokenWhenNoUserToken() {
            // Given
            String configToken = "config-bitbucket-token";
            when(session.getAttribute("bitbucketToken")).thenReturn(null);
            bitbucketConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveBitbucketToken(session);

            // Then
            assertThat(result).isEqualTo(configToken);
        }

        @Test
        @DisplayName("should return config token when user token is blank")
        void shouldReturnConfigTokenWhenUserTokenIsBlank() {
            // Given
            String configToken = "config-bitbucket-token";
            when(session.getAttribute("bitbucketToken")).thenReturn("");
            bitbucketConfig.setToken(configToken);

            // When
            String result = tokenService.getEffectiveBitbucketToken(session);

            // Then
            assertThat(result).isEqualTo(configToken);
        }
    }

    @Nested
    @DisplayName("Token Storage")
    class TokenStorage {

        @Test
        @DisplayName("should store Jira token in session")
        void shouldStoreJiraTokenInSession() {
            // Given
            String token = "new-jira-token";
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.setUserJiraToken(session, token);

            // Then
            verify(session).setAttribute("jiraToken", token);
            verify(session, never()).removeAttribute(anyString());
        }

        @Test
        @DisplayName("should remove Jira token when null is provided")
        void shouldRemoveJiraTokenWhenNull() {
            // Given
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.setUserJiraToken(session, null);

            // Then
            verify(session).removeAttribute("jiraToken");
            verify(session, never()).setAttribute(anyString(), any());
        }

        @Test
        @DisplayName("should remove Jira token when blank string is provided")
        void shouldRemoveJiraTokenWhenBlank() {
            // Given
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.setUserJiraToken(session, "   ");

            // Then
            verify(session).removeAttribute("jiraToken");
            verify(session, never()).setAttribute(anyString(), any());
        }

        @Test
        @DisplayName("should store Bitbucket token in session")
        void shouldStoreBitbucketTokenInSession() {
            // Given
            String token = "new-bitbucket-token";
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.setUserBitbucketToken(session, token);

            // Then
            verify(session).setAttribute("bitbucketToken", token);
            verify(session, never()).removeAttribute(anyString());
        }

        @Test
        @DisplayName("should remove Bitbucket token when blank is provided")
        void shouldRemoveBitbucketTokenWhenBlank() {
            // Given
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.setUserBitbucketToken(session, "");

            // Then
            verify(session).removeAttribute("bitbucketToken");
            verify(session, never()).setAttribute(anyString(), any());
        }
    }

    @Nested
    @DisplayName("Token Clearing")
    class TokenClearing {

        @Test
        @DisplayName("should clear Jira token from session")
        void shouldClearJiraToken() {
            // Given
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.clearUserJiraToken(session);

            // Then
            verify(session).removeAttribute("jiraToken");
        }

        @Test
        @DisplayName("should clear Bitbucket token from session")
        void shouldClearBitbucketToken() {
            // Given
            when(session.getId()).thenReturn("session-123");

            // When
            tokenService.clearUserBitbucketToken(session);

            // Then
            verify(session).removeAttribute("bitbucketToken");
        }
    }

    @Nested
    @DisplayName("Token Availability Checks")
    class TokenAvailabilityChecks {

        @Test
        @DisplayName("should return true when Jira user token exists")
        void shouldReturnTrueWhenJiraUserTokenExists() {
            // Given
            when(session.getAttribute("jiraToken")).thenReturn("user-token");

            // When
            boolean result = tokenService.hasJiraToken(session);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when Jira config token exists")
        void shouldReturnTrueWhenJiraConfigTokenExists() {
            // Given
            when(session.getAttribute("jiraToken")).thenReturn(null);
            jiraConfig.setToken("config-token");

            // When
            boolean result = tokenService.hasJiraToken(session);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no Jira token exists")
        void shouldReturnFalseWhenNoJiraTokenExists() {
            // Given
            when(session.getAttribute("jiraToken")).thenReturn(null);
            jiraConfig.setToken(null);

            // When
            boolean result = tokenService.hasJiraToken(session);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira token is blank")
        void shouldReturnFalseWhenJiraTokenIsBlank() {
            // Given
            when(session.getAttribute("jiraToken")).thenReturn("   ");
            jiraConfig.setToken("");

            // When
            boolean result = tokenService.hasJiraToken(session);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when Bitbucket user token exists")
        void shouldReturnTrueWhenBitbucketUserTokenExists() {
            // Given
            when(session.getAttribute("bitbucketToken")).thenReturn("user-token");

            // When
            boolean result = tokenService.hasBitbucketToken(session);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when no Bitbucket token exists")
        void shouldReturnFalseWhenNoBitbucketTokenExists() {
            // Given
            when(session.getAttribute("bitbucketToken")).thenReturn(null);
            bitbucketConfig.setToken(null);

            // When
            boolean result = tokenService.hasBitbucketToken(session);

            // Then
            assertThat(result).isFalse();
        }
    }
}





