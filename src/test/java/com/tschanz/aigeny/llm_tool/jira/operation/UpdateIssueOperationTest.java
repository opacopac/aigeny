package com.tschanz.aigeny.llm_tool.jira.operation;

import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UpdateIssueOperation}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateIssueOperation")
class UpdateIssueOperationTest {

    private UpdateIssueOperation operation;

    @Mock
    private JiraHttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() {
        operation = new UpdateIssueOperation(httpClient);
    }

    @Test
    @DisplayName("should successfully update issue fields")
    void shouldSuccessfullyUpdateIssue() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE,
                "TEST-123",
                Map.of("summary", "Updated summary", "priority", Map.of("name", "High")),
                "test description"
        );

        when(httpResponse.statusCode()).thenReturn(204);
        when(httpClient.put(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-123");
        verify(httpClient).put(
                eq("https://jira.example.com/rest/api/2/issue/TEST-123"),
                anyString(),
                eq("Bearer test-token")
        );
    }

    @Test
    @DisplayName("should handle authentication failure")
    void shouldHandleAuthenticationFailure() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE,
                "TEST-123",
                Map.<String, Object>of("summary", "Updated"),
                "test description"
        );

        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.put(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "invalid-token");

        assertThat(result).contains("401");
    }

    @Test
    @DisplayName("should handle forbidden error")
    void shouldHandleForbiddenError() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE,
                "TEST-123",
                Map.<String, Object>of("summary", "Updated"),
                "test description"
        );

        when(httpResponse.statusCode()).thenReturn(403);
        when(httpClient.put(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "test-token");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should handle generic error")
    void shouldHandleGenericError() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.UPDATE_ISSUE,
                "TEST-123",
                Map.<String, Object>of("summary", "Updated"),
                "test description"
        );

        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn("Internal server error");
        when(httpClient.put(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "test-token");

        assertThat(result).contains("500");
    }
}




