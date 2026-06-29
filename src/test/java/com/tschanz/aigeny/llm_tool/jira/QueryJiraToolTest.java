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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

/**
 * Unit tests for {@link QueryJiraTool}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The tool uses the injected {@link JiraHttpClient} (D-4 fix)</li>
 *   <li>Configuration guards (missing base URL, missing token) work correctly</li>
 *   <li>Both query paths (JQL search and issue-by-key) delegate to the HTTP client</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QueryJiraTool")
class QueryJiraToolTest {

    @Mock private JiraConfiguration jiraConfig;
    @Mock private JiraHttpClient jiraHttpClient;
    @Mock private HttpResponse<String> httpResponse;

    private QueryJiraTool tool;
    private JiraIssueFormatter formatter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Minimal Jira issue JSON for single-issue responses
    private static final String SINGLE_ISSUE_JSON = """
            {
              "key": "NOVA-1",
              "fields": {
                "summary": "Test Issue",
                "status":    { "name": "Open" },
                "issuetype": { "name": "Task" },
                "priority":  { "name": "Medium" },
                "assignee":  { "displayName": "Alice" },
                "created":   "2024-01-01",
                "updated":   "2024-01-02"
              }
            }""";

    // Minimal Jira search response JSON
    private static final String SEARCH_RESPONSE_JSON = """
            {
              "total": 1,
              "issues": [
                {
                  "key": "NOVA-1",
                  "fields": {
                    "summary":   "Test Issue",
                    "status":    { "name": "Open" },
                    "assignee":  { "displayName": "Alice" },
                    "priority":  { "name": "Medium" },
                    "issuetype": { "name": "Task" }
                  }
                }
              ]
            }""";

    @BeforeEach
    void setUp() {
        formatter = spy(new JiraIssueFormatter(objectMapper));
        tool = new QueryJiraTool(jiraConfig, objectMapper, jiraHttpClient, formatter);
        when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com");
        JiraTokenContext.set("test-token");
    }

    @AfterEach
    void tearDown() {
        JiraTokenContext.clear();
    }

    // ── Configuration guards ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Configuration guards")
    class ConfigurationGuards {

        @Test
        @DisplayName("returns error and does not call HTTP client when base URL is blank")
        void returnsErrorWhenBaseUrlBlank() throws Exception {
            when(jiraConfig.getBaseUrl()).thenReturn("");

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error and does not call HTTP client when base URL is null")
        void returnsErrorWhenBaseUrlNull() throws Exception {
            when(jiraConfig.getBaseUrl()).thenReturn(null);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error when no token is available")
        void returnsErrorWhenNoToken() throws Exception {
            JiraTokenContext.clear();
            when(jiraConfig.getToken()).thenReturn(null);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(jiraHttpClient);
        }

        @Test
        @DisplayName("returns error when neither issueKey nor jql is provided")
        void returnsErrorWhenNoQueryParams() throws Exception {
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
        @DisplayName("issue-by-key path calls jiraHttpClient.get() with issueKey in URL")
        void issueByKey_callsJiraHttpClient() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SINGLE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            tool.execute("{\"issueKey\":\"NOVA-1\"}");

            verify(jiraHttpClient).get(contains("NOVA-1"), eq("Bearer test-token"));
        }

        @Test
        @DisplayName("JQL search path calls jiraHttpClient.get() with encoded JQL in URL")
        void jqlSearch_callsJiraHttpClient() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SEARCH_RESPONSE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            tool.execute("{\"jql\":\"project = NOVA AND status = Open\"}");

            verify(jiraHttpClient).get(contains("search"), eq("Bearer test-token"));
        }

        @Test
        @DisplayName("uses server config token as fallback when ThreadLocal token is absent")
        void usesServerConfigTokenWhenThreadLocalAbsent() throws Exception {
            JiraTokenContext.clear();
            when(jiraConfig.getToken()).thenReturn("server-token");
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SINGLE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            tool.execute("{\"issueKey\":\"NOVA-1\"}");

            verify(jiraHttpClient).get(anyString(), eq("Bearer server-token"));
        }
    }

    // ── Response handling ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Response handling")
    class ResponseHandling {

        @Test
        @DisplayName("returns 401 error message on authentication failure")
        void returns401ErrorMessage() throws Exception {
            when(httpResponse.statusCode()).thenReturn(401);
            when(httpResponse.body()).thenReturn("Unauthorized");
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).isNotBlank();
        }

        @Test
        @DisplayName("returns 404 error message when issue is not found")
        void returns404ErrorMessage() throws Exception {
            when(httpResponse.statusCode()).thenReturn(404);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-MISSING\"}");

            assertThat(result.getText()).isNotBlank();
        }

        @Test
        @DisplayName("successful single-issue response includes issue key in result text")
        void successfulIssueByKeyIncludesKey() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SINGLE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).contains("NOVA-1");
        }

        @Test
        @DisplayName("successful JQL search includes found issues count in result text")
        void successfulJqlSearchIncludesIssues() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SEARCH_RESPONSE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            ToolResult result = tool.execute("{\"jql\":\"project = NOVA\"}");

            assertThat(result.getText()).contains("NOVA-1");
        }
    }

    // ── Delegation to JiraIssueFormatter (S-2) ───────────────────────────────

    @Nested
    @DisplayName("Delegates formatting to JiraIssueFormatter (S-2)")
    class DelegatesFormatting {

        @Test
        @DisplayName("single-issue path delegates to formatter.formatSingleIssue()")
        void issueByKey_delegatesToFormatter() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SINGLE_ISSUE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            tool.execute("{\"issueKey\":\"NOVA-1\"}");

            verify(formatter).formatSingleIssue(SINGLE_ISSUE_JSON);
        }

        @Test
        @DisplayName("JQL search path delegates to formatter.formatSearchResponse()")
        void jqlSearch_delegatesToFormatter() throws Exception {
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(SEARCH_RESPONSE_JSON);
            when(jiraHttpClient.get(anyString(), anyString())).thenReturn(httpResponse);

            tool.execute("{\"jql\":\"project = NOVA\"}");

            verify(formatter).formatSearchResponse(SEARCH_RESPONSE_JSON);
        }
    }
}
