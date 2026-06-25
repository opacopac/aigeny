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

@ExtendWith(MockitoExtension.class)
@DisplayName("AddCommentOperation")
class AddCommentOperationTest {

    private AddCommentOperation operation;

    @Mock
    private JiraHttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() {
        operation = new AddCommentOperation(httpClient);
    }

    @Test
    @DisplayName("should successfully add comment to issue")
    void shouldSuccessfullyAddComment() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, "TEST-456",
                Map.<String, Object>of("comment", "This is a test comment"), "desc");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-456");
        verify(httpClient).post(
                eq("https://jira.example.com/rest/api/2/issue/TEST-456/comment"),
                anyString(), eq("Bearer test-token"));
    }

    @Test
    @DisplayName("should handle authentication failure")
    void shouldHandleAuthenticationFailure() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, "TEST-456",
                Map.<String, Object>of("comment", "Test comment"), "desc");

        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "invalid-token");

        assertThat(result).contains("401");
    }

    @Test
    @DisplayName("should handle forbidden error")
    void shouldHandleForbiddenError() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, "TEST-456",
                Map.<String, Object>of("comment", "Test comment"), "desc");

        when(httpResponse.statusCode()).thenReturn(403);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(action, "https://jira.example.com", "test-token");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should URL encode issue key in request")
    void shouldUrlEncodeIssueKey() throws Exception {
        PendingJiraAction action = new PendingJiraAction(
                PendingJiraAction.ActionType.ADD_COMMENT, "TEST-123",
                Map.<String, Object>of("comment", "Comment"), "desc");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        operation.execute(action, "https://jira.example.com", "test-token");

        verify(httpClient).post(
                eq("https://jira.example.com/rest/api/2/issue/TEST-123/comment"),
                anyString(), anyString());
    }
}
