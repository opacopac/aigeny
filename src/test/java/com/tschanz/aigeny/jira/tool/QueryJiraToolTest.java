package com.tschanz.aigeny.jira.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.jira.JiraHttpClient;
import com.tschanz.aigeny.jira.JiraIssueFormatter;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link QueryJiraTool}.
 */
@DisplayName("QueryJiraTool")
class QueryJiraToolTest {

    private static final String BASE_URL  = "https://jira.example.com";
    private static final String TOKEN     = "test-token";
    private static final String ISSUE_KEY = "NOVA-100";

    private JiraConfiguration   jiraConfig;
    private JiraHttpClient      jiraHttpClient;
    private JiraIssueFormatter  formatter;
    private QueryJiraTool       tool;
    private ObjectMapper        objectMapper;

    // Minimal single-issue JSON response from Jira REST API
    private static final String SINGLE_ISSUE_JSON = """
            {
              "key": "NOVA-100",
              "fields": {
                "summary": "Test issue",
                "status": {"name": "Open"},
                "issuetype": {"name": "Bug"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Alice"},
                "created": "2026-01-01T10:00:00.000+0000",
                "updated": "2026-06-01T12:00:00.000+0000",
                "duedate": "2026-07-31",
                "description": null,
                "comment": {"comments": []},
                "attachment": [],
                "issuelinks": [],
                "subtasks": []
              }
            }
            """;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        objectMapper   = new ObjectMapper();
        jiraConfig     = mock(JiraConfiguration.class);
        jiraHttpClient = mock(JiraHttpClient.class);
        formatter      = new JiraIssueFormatter(objectMapper);

        when(jiraConfig.getBaseUrl()).thenReturn(BASE_URL);
        when(jiraConfig.getToken()).thenReturn(TOKEN);

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(SINGLE_ISSUE_JSON);
        when(jiraHttpClient.get(anyString(), anyString())).thenReturn(mockResponse);

        tool = new QueryJiraTool(jiraConfig, objectMapper, jiraHttpClient, formatter);
    }

    // ── fetchIssueByKey URL ───────────────────────────────────────────────────

    @Nested
    @DisplayName("fetchIssueByKey – URL fields parameter")
    class FetchIssueByKeyUrl {

        @Test
        @DisplayName("includes 'duedate' in the fields query parameter")
        void includesDuedateInFields() throws Exception {
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            tool.execute("{\"issueKey\": \"" + ISSUE_KEY + "\"}");

            verify(jiraHttpClient).get(urlCaptor.capture(), anyString());
            assertThat(urlCaptor.getValue())
                    .contains("fields=")
                    .contains("duedate");
        }

        @Test
        @DisplayName("still includes all original fields alongside duedate")
        void includesAllOriginalFields() throws Exception {
            ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

            tool.execute("{\"issueKey\": \"" + ISSUE_KEY + "\"}");

            verify(jiraHttpClient).get(urlCaptor.capture(), anyString());
            String url = urlCaptor.getValue();
            assertThat(url)
                    .contains("summary")
                    .contains("status")
                    .contains("assignee")
                    .contains("priority")
                    .contains("issuetype")
                    .contains("created")
                    .contains("updated")
                    .contains("duedate")
                    .contains("description")
                    .contains("comment")
                    .contains("attachment")
                    .contains("issuelinks")
                    .contains("subtasks");
        }

        @Test
        @DisplayName("result contains Due Date from response when duedate is present")
        void resultContainsDueDate() throws Exception {
            ToolResult result = tool.execute("{\"issueKey\": \"" + ISSUE_KEY + "\"}");

            assertThat(result.getText())
                    .contains("Due Date")
                    .contains("2026-07-31");
        }
    }

    // ── getCallDescription ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCallDescription()")
    class GetCallDescription {

        @Test
        @DisplayName("returns issue key label for issueKey argument")
        void returnsIssuekeyLabel() {
            String desc = tool.getCallDescription("{\"issueKey\": \"NOVA-42\"}");
            assertThat(desc).contains("NOVA-42");
        }

        @Test
        @DisplayName("returns JQL label for jql argument")
        void returnsJqlLabel() {
            String desc = tool.getCallDescription("{\"jql\": \"project = NOVA AND status = Open\"}");
            assertThat(desc).contains("project = NOVA");
        }

        @Test
        @DisplayName("truncates long JQL to 60 characters")
        void truncatesLongJql() {
            String longJql = "project = NOVA AND status = Open AND assignee = currentUser() ORDER BY created DESC";
            String desc = tool.getCallDescription("{\"jql\": \"" + longJql + "\"}");
            assertThat(desc).endsWith("...");
            // description prefix + max 57 chars of JQL + "..."
            assertThat(desc.length()).isLessThanOrEqualTo("Jira-Suche: ".length() + 60);
        }
    }
}

