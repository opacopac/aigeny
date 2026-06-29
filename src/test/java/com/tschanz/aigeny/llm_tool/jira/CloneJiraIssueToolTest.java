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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloneJiraIssueTool}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The tool uses the injected {@link JiraHttpClient} (D-4 fix – no longer creates
 *       its own {@code java.net.http.HttpClient})</li>
 *   <li>Configuration and authorisation guards work correctly</li>
 *   <li>Write-mode guard blocks execution when Jira write mode is disabled</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloneJiraIssueTool")
class CloneJiraIssueToolTest {

    @Mock private JiraConfiguration jiraConfig;
    @Mock private JiraHttpClient jiraHttpClient;
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
        tool = new CloneJiraIssueTool(jiraConfig, objectMapper, jiraHttpClient);
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
            // ConfirmationContext not set → tool returns "no_streaming_context" after HTTP call
            ConfirmationContext.clear();

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            verify(jiraHttpClient).get(contains("NOVA-100"), anyString());
        }

        @Test
        @DisplayName("uses Bearer token from ThreadLocal in HTTP requests")
        void usesBearerToken() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SOURCE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);
            ConfirmationContext.clear();

            tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            verify(jiraHttpClient).get(anyString(), contains("Bearer test-token"));
        }

        @Test
        @DisplayName("returns error result when source issue is not found (404)")
        void returnsErrorOn404() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            when(httpResponse.body()).thenReturn("Not Found");
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-MISSING\"}");

            assertThat(result.getText()).isNotBlank();
        }

        @Test
        @DisplayName("returns error result when Jira returns 401")
        void returnsErrorOn401() throws Exception {
            when(httpResponse.statusCode()).thenReturn(401);
            when(httpResponse.body()).thenReturn("Unauthorized");
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"sourceIssueKey\":\"NOVA-100\"}");

            assertThat(result.getText()).isNotBlank();
        }
    }
}



