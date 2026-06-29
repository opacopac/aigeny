package com.tschanz.aigeny.llm_tool.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm_tool.ToolResult;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloneJiraIssueTool}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The tool uses the injected {@link JiraHttpClient} – no own {@code java.net.http.HttpClient}
 *       and no sentinel-node pattern (S-3 / D-4 fix)</li>
 *   <li>The tool uses the injected {@link ConfirmationService} instead of the static
 *       {@link ConfirmationContext} (D-1 fix)</li>
 *   <li>Configuration and authorisation guards work correctly</li>
 *   <li>Write-mode guard blocks execution when Jira write mode is disabled</li>
 *   <li>Subtask cloning fetches each sub-task via the injected HTTP client</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloneJiraIssueTool")
class CloneJiraIssueToolTest {

    @Mock private JiraConfiguration jiraConfig;
    @Mock private JiraHttpClient jiraHttpClient;
    @Mock private ConfirmationService confirmationService;
    @Mock private HttpResponse<String> httpResponse;

    private CloneJiraIssueTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SOURCE_ISSUE_JSON = """
            {
              "key": "NOVA-100",
              "fields": {
                "summary":     "Original Issue",
                "description": "Some description",
                "issuetype":   { "name": "Task" },
                "priority":    { "name": "Medium" },
                "assignee":    { "name": "u123456", "displayName": "Alice" },
                "project":     { "key": "NOVA" },
                "subtasks":    []
              }
            }""";

    @BeforeEach
    void setUp() {
        tool = new CloneJiraIssueTool(jiraConfig, objectMapper, jiraHttpClient, confirmationService);
        // lenient: tests that check write-mode guard never reach getBaseUrl()
        lenient().when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com");
        JiraTokenContext.set("test-token");
        JiraWriteContext.set(true);
    }

    @AfterEach
    void tearDown() {
        JiraTokenContext.clear();
        JiraWriteContext.set(false);
    }

    // ── Configuration and authorisation guards ────────────────────────────────

    @Nested
    @DisplayName("Configuration guards")
    class ConfigurationGuards {

        @Test
        @DisplayName("returns error and does not call HTTP client when write mode is disabled")
        void returnsErrorWhenWriteModeDisabled() throws Exception {
            JiraWriteContext.set(false);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error when base URL is blank")
        void returnsErrorWhenBaseUrlBlank() throws Exception {
            when(jiraConfig.getBaseUrl()).thenReturn("");

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error when no token is available")
        void returnsErrorWhenNoToken() throws Exception {
            JiraTokenContext.clear();
            when(jiraConfig.getToken()).thenReturn(null);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error when sourceIssueKey is missing")
        void returnsErrorWhenSourceKeyMissing() throws Exception {
            ToolResult result = tool.execute("{}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }
    }

    // ── Injected JiraHttpClient usage (D-4) ───────────────────────────────────

    @Nested
    @DisplayName("Uses injected JiraHttpClient (D-4)")
    class UsesInjectedHttpClient {

        @Test
        @DisplayName("calls jiraHttpClient.get() with source issue key in URL")
        void callsJiraHttpClientWithSourceKey() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SOURCE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);
            // confirmationService not available → tool returns "no_streaming_context" after HTTP call
            when(confirmationService.isAvailable()).thenReturn(false);

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            verify(jiraHttpClient).get(contains("NOVA-100"), anyString());
        }

        @Test
        @DisplayName("uses Bearer token from ThreadLocal in HTTP requests")
        void usesBearerToken() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SOURCE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);
            when(confirmationService.isAvailable()).thenReturn(false);

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            verify(jiraHttpClient).get(anyString(), contains("Bearer test-token"));
        }

        @Test
        @DisplayName("returns error result when source issue is not found (404)")
        void returnsErrorOn404() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-MISSING\"}");

            assertThat(result.getText()).isNotBlank();
        }

        @Test
        @DisplayName("returns error result when Jira returns 401")
        void returnsErrorOn401() throws Exception {
            when(httpResponse.statusCode()).thenReturn(401);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
        }
    }

    // ── Injected ConfirmationService usage (D-1) ──────────────────────────────

    @Nested
    @DisplayName("Uses injected ConfirmationService (D-1)")
    class UsesInjectedConfirmationService {

        @Test
        @DisplayName("returns no-streaming error when confirmationService.isAvailable() is false")
        void returnsErrorWhenConfirmationServiceNotAvailable() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SOURCE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);
            when(confirmationService.isAvailable()).thenReturn(false);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
            verify(confirmationService, never()).requestConfirmation(anyString(), any());
        }

        @Test
        @DisplayName("delegates to confirmationService.requestConfirmation() when available")
        void delegatesToConfirmationServiceWhenAvailable() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SOURCE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult expected = new ToolResult("confirmed!");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(expected);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result).isSameAs(expected);
            verify(confirmationService).requestConfirmation(contains("NOVA-100"), any(PendingJiraAction.class));
        }
    }

    // ── Subtask cloning ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Subtask cloning")
    class SubtaskCloning {

        private static final String SOURCE_WITH_SUBTASKS_JSON = """
                {
                  "key": "NOVA-100",
                  "fields": {
                    "summary":     "Parent Issue",
                    "description": "",
                    "issuetype":   { "name": "Story" },
                    "assignee":    { "name": "u123456", "displayName": "Alice" },
                    "project":     { "key": "NOVA" },
                    "subtasks": [
                      { "key": "NOVA-101" },
                      { "key": "NOVA-102" }
                    ]
                  }
                }""";

        private static final String SUBTASK_JSON = """
                {
                  "key": "NOVA-101",
                  "fields": {
                    "summary":   "Sub-task A",
                    "issuetype": { "name": "Sub-task" },
                    "assignee":  { "name": "u123456" },
                    "description": ""
                  }
                }""";

        @Test
        @DisplayName("fetches each subtask via jiraHttpClient when cloneSubtasks=true")
        void fetchesEachSubtaskViaHttpClient() throws Exception {
            HttpResponse<String> parentResp   = mockResponseWith(200, SOURCE_WITH_SUBTASKS_JSON);
            HttpResponse<String> subtaskResp  = mockResponseWith(200, SUBTASK_JSON);

            when(jiraHttpClient.get(contains("NOVA-100"), anyString())).thenReturn(parentResp);
            when(jiraHttpClient.get(contains("NOVA-101"), anyString())).thenReturn(subtaskResp);
            when(jiraHttpClient.get(contains("NOVA-102"), anyString())).thenReturn(subtaskResp);
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(new ToolResult("ok"));

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\",\"cloneSubtasks\":true}");

            verify(jiraHttpClient).get(contains("NOVA-101"), anyString());
            verify(jiraHttpClient).get(contains("NOVA-102"), anyString());
        }

        @Test
        @DisplayName("confirmation description mentions subtask count when cloneSubtasks=true")
        void confirmationMentionsSubtaskCount() throws Exception {
            HttpResponse<String> parentResp   = mockResponseWith(200, SOURCE_WITH_SUBTASKS_JSON);
            HttpResponse<String> subtaskResp  = mockResponseWith(200, SUBTASK_JSON);

            when(jiraHttpClient.get(contains("NOVA-100"), anyString())).thenReturn(parentResp);
            when(jiraHttpClient.get(contains("NOVA-101"), anyString())).thenReturn(subtaskResp);
            when(jiraHttpClient.get(contains("NOVA-102"), anyString())).thenReturn(subtaskResp);
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(new ToolResult("ok"));

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\",\"cloneSubtasks\":true}");

            verify(confirmationService).requestConfirmation(contains("Sub-Tasks"), any());
        }

        @Test
        @DisplayName("skips subtask when HTTP fetch returns non-200 and continues with rest")
        void skipsFailedSubtaskAndContinues() throws Exception {
            HttpResponse<String> parentResp  = mockResponseWith(200, SOURCE_WITH_SUBTASKS_JSON);
            HttpResponse<String> failResp    = mockResponseWith(500, "Internal Error");
            HttpResponse<String> subtaskResp = mockResponseWith(200, SUBTASK_JSON);

            when(jiraHttpClient.get(contains("NOVA-100"), anyString())).thenReturn(parentResp);
            when(jiraHttpClient.get(contains("NOVA-101"), anyString())).thenReturn(failResp);
            when(jiraHttpClient.get(contains("NOVA-102"), anyString())).thenReturn(subtaskResp);
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(new ToolResult("ok"));

            // Should not throw – failed subtask is skipped
            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\",\"cloneSubtasks\":true}");

            assertThat(result.getText()).isEqualTo("ok");
        }

        @Test
        @DisplayName("does not fetch subtasks when cloneSubtasks is false (default)")
        void doesNotFetchSubtasksWhenDisabled() throws Exception {
            HttpResponse<String> parentResp = mockResponseWith(200, SOURCE_WITH_SUBTASKS_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(parentResp);
            when(confirmationService.isAvailable()).thenReturn(false);

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            // Only the parent issue fetch, no subtask URLs
            verify(jiraHttpClient, times(1)).get(anyString(), anyString());
        }

        @SuppressWarnings("unchecked")
        private HttpResponse<String> mockResponseWith(int status, String body) {
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(status);
            lenient().when(resp.body()).thenReturn(body);
            return resp;
        }
    }
}
