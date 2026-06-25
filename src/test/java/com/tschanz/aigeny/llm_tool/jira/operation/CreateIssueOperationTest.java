package com.tschanz.aigeny.llm_tool.jira.operation;

import com.tschanz.aigeny.llm_tool.jira.PendingJiraAction;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import com.tschanz.aigeny.llm_tool.jira.service.IssueLinkService;
import com.tschanz.aigeny.llm_tool.jira.service.SubtaskCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateIssueOperation")
class CreateIssueOperationTest {

    private CreateIssueOperation operation;

    @Mock private JiraHttpClient httpClient;
    @Mock private SubtaskCreationService subtaskService;
    @Mock private IssueLinkService linkService;
    @Mock private HttpResponse<String> httpResponse;

    @BeforeEach
    void setUp() {
        operation = new CreateIssueOperation(httpClient, subtaskService, linkService);
    }

    private PendingJiraAction createAction(Map<String, Object> params) {
        return new PendingJiraAction(PendingJiraAction.ActionType.CREATE_ISSUE, null, params, "desc");
    }

    @Test
    @DisplayName("should successfully create issue without subtasks")
    void shouldSuccessfullyCreateIssue() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "New issue");
        params.put("issuetype", "Task");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"key\":\"TEST-789\"}");
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(createAction(params), "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-789");
        verify(httpClient).post(eq("https://jira.example.com/rest/api/2/issue"), anyString(), eq("Bearer test-token"));
        verifyNoInteractions(subtaskService, linkService);
    }

    @Test
    @DisplayName("should create issue with clone link when clonedFrom is provided")
    void shouldCreateCloneLinkWhenClonedFrom() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "Cloned issue");
        params.put("clonedFrom", "TEST-100");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"key\":\"TEST-200\"}");
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(createAction(params), "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-200");
        verify(linkService).createCloneLink("TEST-100", "TEST-200", "https://jira.example.com", "test-token");
    }

    @Test
    @DisplayName("should create issue with subtasks")
    void shouldCreateIssueWithSubtasks() throws Exception {
        List<Map<String, String>> subtasks = List.of(
                Map.of("summary", "Subtask 1", "issuetype", "Sub-task"),
                Map.of("summary", "Subtask 2", "issuetype", "Sub-task")
        );
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "Parent issue");
        params.put("subtasks", subtasks);

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"key\":\"TEST-300\"}");
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);
        when(subtaskService.createSubtasks(anyString(), anyString(), anyList(), anyString(), anyString())).thenReturn(2);

        String result = operation.execute(createAction(params), "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-300");
        verify(subtaskService).createSubtasks("TEST-300", "TEST", subtasks, "https://jira.example.com", "test-token");
    }

    @Test
    @DisplayName("should handle authentication failure")
    void shouldHandleAuthenticationFailure() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "New issue");

        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(createAction(params), "https://jira.example.com", "invalid-token");

        assertThat(result).contains("401");
    }

    @Test
    @DisplayName("should handle forbidden error")
    void shouldHandleForbiddenError() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "New issue");

        when(httpResponse.statusCode()).thenReturn(403);
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(createAction(params), "https://jira.example.com", "test-token");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should include optional fields when provided")
    void shouldIncludeOptionalFields() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("projectKey", "TEST");
        params.put("summary", "Issue with all fields");
        params.put("description", "Detailed description");
        params.put("assignee", "john.doe");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"key\":\"TEST-400\"}");
        when(httpClient.post(anyString(), anyString(), anyString())).thenReturn(httpResponse);

        String result = operation.execute(createAction(params), "https://jira.example.com", "test-token");

        assertThat(result).contains("TEST-400");
        verify(httpClient).post(anyString(), contains("description"), anyString());
    }
}
