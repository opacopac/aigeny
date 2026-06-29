package com.tschanz.aigeny.llm_tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.BitbucketConfiguration;
import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm_tool.db.OracleConnectionPool;
import com.tschanz.aigeny.llm_tool.bitbucket.ReadBitbucketFileTool;
import com.tschanz.aigeny.llm_tool.bitbucket.SearchBitbucketTool;
import com.tschanz.aigeny.llm_tool.db.OracleDbTool;
import com.tschanz.aigeny.llm_tool.jira.*;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Verifies that each migrated tool correctly implements {@link AbstractTool} and that
 * their {@code getCallDescription} overrides still work after migration.
 * Also verifies that tools which don't override {@code getCallDescription}
 * inherit the JSON-description-field behaviour from {@link AbstractTool}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tool getCallDescription after AbstractTool migration")
class ToolGetCallDescriptionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private JiraConfiguration jiraConfig;
    @Mock private BitbucketConfiguration bitbucketConfig;
    @Mock private OracleConnectionPool oracleConnectionPool;
    @Mock private JiraHttpClient jiraHttpClient;

    @BeforeEach
    void setUp() {
        lenient().when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com");
        lenient().when(bitbucketConfig.getBaseUrl()).thenReturn("https://bb.example.com");
    }

    // ── OracleDbTool ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OracleDbTool (inherits AbstractTool default)")
    class OracleDbToolDesc {

        private OracleDbTool tool;

        @BeforeEach void init() { tool = new OracleDbTool(oracleConnectionPool, objectMapper); }

        @Test
        @DisplayName("returns 'description' field from JSON")
        void returnsDescriptionField() {
            assertThat(tool.getCallDescription("{\"sql\":\"SELECT 1\",\"description\":\"Count rows\"}"))
                    .isEqualTo("Count rows");
        }

        @Test
        @DisplayName("falls back to tool name when no description")
        void fallsBackToName() {
            assertThat(tool.getCallDescription("{\"sql\":\"SELECT 1\"}"))
                    .isEqualTo("query_oracle_db");
        }
    }

    // ── QueryJiraTool ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("QueryJiraTool")
    class QueryJiraToolDesc {

        private QueryJiraTool tool;

        @BeforeEach void init() { tool = new QueryJiraTool(jiraConfig, objectMapper, jiraHttpClient); }

        @Test
        @DisplayName("returns 'Jira-Ticket lesen: KEY' when issueKey is given")
        void issueKeyDescription() {
            assertThat(tool.getCallDescription("{\"issueKey\":\"NOVA-1234\"}"))
                    .isEqualTo("Jira-Ticket lesen: NOVA-1234");
        }

        @Test
        @DisplayName("returns truncated JQL when jql is given")
        void jqlDescription() {
            assertThat(tool.getCallDescription("{\"jql\":\"project = NOVA AND status = Open\"}"))
                    .contains("Jira-Suche:");
        }

        @Test
        @DisplayName("falls back to default name when no fields")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isEqualTo("Jira durchsuchen");
        }
    }

    // ── AddJiraCommentTool ────────────────────────────────────────────────────

    @Nested
    @DisplayName("AddJiraCommentTool")
    class AddJiraCommentToolDesc {

        private AddJiraCommentTool tool;

        @BeforeEach void init() { tool = new AddJiraCommentTool(jiraConfig, objectMapper); }

        @Test
        @DisplayName("includes issue key in description")
        void withIssueKey() {
            assertThat(tool.getCallDescription("{\"issueKey\":\"NOVA-42\",\"comment\":\"Done\"}"))
                    .contains("NOVA-42");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── UpdateJiraIssueTool ───────────────────────────────────────────────────

    @Nested
    @DisplayName("UpdateJiraIssueTool")
    class UpdateJiraIssueToolDesc {

        private UpdateJiraIssueTool tool;

        @BeforeEach void init() { tool = new UpdateJiraIssueTool(jiraConfig, objectMapper); }

        @Test
        @DisplayName("includes issue key and field name in description")
        void withIssueKeyAndSummary() {
            String desc = tool.getCallDescription("{\"issueKey\":\"NOVA-7\",\"summary\":\"New title\"}");
            assertThat(desc).contains("NOVA-7").contains("Summary");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── CreateJiraIssueTool ───────────────────────────────────────────────────

    @Nested
    @DisplayName("CreateJiraIssueTool")
    class CreateJiraIssueToolDesc {

        private CreateJiraIssueTool tool;

        @BeforeEach void init() { tool = new CreateJiraIssueTool(jiraConfig, objectMapper); }

        @Test
        @DisplayName("includes project and summary in description")
        void withProjectAndSummary() {
            String desc = tool.getCallDescription("{\"project\":\"NOVA\",\"summary\":\"Fix login bug\"}");
            assertThat(desc).contains("NOVA").contains("Fix login bug");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── CloneJiraIssueTool ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CloneJiraIssueTool")
    class CloneJiraIssueToolDesc {

        private CloneJiraIssueTool tool;

        @BeforeEach void init() { tool = new CloneJiraIssueTool(jiraConfig, objectMapper, jiraHttpClient); }

        @Test
        @DisplayName("includes source and target project in description")
        void withSourceAndTarget() {
            String desc = tool.getCallDescription(
                    "{\"sourceIssueKey\":\"NOVA-1\",\"targetProject\":\"MVP\",\"cloneSubtasks\":false}");
            assertThat(desc).contains("NOVA-1").contains("MVP");
        }

        @Test
        @DisplayName("includes subtask hint when cloneSubtasks is true")
        void withSubtasks() {
            String desc = tool.getCallDescription(
                    "{\"sourceIssueKey\":\"NOVA-1\",\"cloneSubtasks\":true}");
            assertThat(desc).contains("Sub-Task");
        }
    }

    // ── ReadJiraAttachmentTool ────────────────────────────────────────────────

    @Nested
    @DisplayName("ReadJiraAttachmentTool")
    class ReadJiraAttachmentToolDesc {

        private ReadJiraAttachmentTool tool;

        @BeforeEach void init() { tool = new ReadJiraAttachmentTool(jiraConfig, objectMapper); }

        @Test
        @DisplayName("returns filename when provided")
        void withFilename() {
            assertThat(tool.getCallDescription("{\"filename\":\"report.xlsx\",\"attachmentUrl\":\"http://x\"}"))
                    .contains("report.xlsx");
        }

        @Test
        @DisplayName("falls back to attachment URL filename when filename absent")
        void fromUrl() {
            assertThat(tool.getCallDescription("{\"attachmentUrl\":\"http://jira/attachments/data.csv\"}"))
                    .contains("data.csv");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── SearchJiraUserTool ────────────────────────────────────────────────────

    @Nested
    @DisplayName("SearchJiraUserTool")
    class SearchJiraUserToolDesc {

        private SearchJiraUserTool tool;

        @BeforeEach void init() { tool = new SearchJiraUserTool(jiraConfig, objectMapper); }

        @Test
        @DisplayName("includes display name in description")
        void withDisplayName() {
            assertThat(tool.getCallDescription("{\"displayName\":\"Max Mustermann\"}"))
                    .contains("Max Mustermann");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── ReadBitbucketFileTool ─────────────────────────────────────────────────

    @Nested
    @DisplayName("ReadBitbucketFileTool")
    class ReadBitbucketFileToolDesc {

        private ReadBitbucketFileTool tool;

        @BeforeEach void init() { tool = new ReadBitbucketFileTool(bitbucketConfig, objectMapper); }

        @Test
        @DisplayName("includes project, repo and file path in description")
        void withAllFields() {
            String desc = tool.getCallDescription(
                    "{\"projectKey\":\"NOVA\",\"repoSlug\":\"novap_pflege\",\"filePath\":\"src/Foo.java\"}");
            assertThat(desc).contains("NOVA").contains("novap_pflege").contains("Foo.java");
        }

        @Test
        @DisplayName("falls back gracefully on empty JSON")
        void fallback() {
            assertThat(tool.getCallDescription("{}")).isNotBlank();
        }
    }

    // ── SearchBitbucketTool ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SearchBitbucketTool")
    class SearchBitbucketToolDesc {

        private SearchBitbucketTool tool;

        @BeforeEach void init() { tool = new SearchBitbucketTool(bitbucketConfig, objectMapper); }

        @Test
        @DisplayName("'list_repos' action description")
        void listRepos() {
            assertThat(tool.getCallDescription("{\"action\":\"list_repos\",\"projectKey\":\"NOVA\"}"))
                    .contains("Repos");
        }

        @Test
        @DisplayName("'search_code' action includes query")
        void searchCode() {
            String desc = tool.getCallDescription(
                    "{\"action\":\"search_code\",\"query\":\"MyClass\",\"projectKey\":\"NOVA\"}");
            assertThat(desc).contains("MyClass");
        }

        @Test
        @DisplayName("falls back gracefully on unknown action")
        void unknownAction() {
            assertThat(tool.getCallDescription("{\"action\":\"unknown_action\"}"))
                    .contains("Bitbucket");
        }
    }
}
