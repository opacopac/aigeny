package com.tschanz.aigeny.llm_tool.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm_tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JiraIssueFormatter}.
 *
 * <p>Tests are intentionally free of mocks – the formatter is a pure data-transformation
 * component with no external dependencies beyond {@link ObjectMapper}.
 */
@DisplayName("JiraIssueFormatter")
class JiraIssueFormatterTest {

    private JiraIssueFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new JiraIssueFormatter(new ObjectMapper());
    }

    // ── formatSingleIssue ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("formatSingleIssue()")
    class FormatSingleIssue {

        @Test
        @DisplayName("includes issue key and summary in output")
        void includesKeyAndSummary() throws Exception {
            String json = """
                    {
                      "key": "NOVA-42",
                      "fields": {
                        "summary": "Fix the flux capacitor",
                        "status":    { "name": "In Progress" },
                        "issuetype": { "name": "Bug" },
                        "priority":  { "name": "High" },
                        "assignee":  { "displayName": "Marty McFly" },
                        "created":   "2025-01-01",
                        "updated":   "2025-01-02"
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("NOVA-42")
                    .contains("Fix the flux capacitor");
        }

        @Test
        @DisplayName("includes all basic metadata fields")
        void includesBasicFields() throws Exception {
            String json = """
                    {
                      "key": "NOVA-1",
                      "fields": {
                        "summary": "Summary",
                        "status":    { "name": "Open" },
                        "issuetype": { "name": "Story" },
                        "priority":  { "name": "Medium" },
                        "assignee":  { "displayName": "Alice" },
                        "created":   "2024-03-01",
                        "updated":   "2024-03-10"
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Open")
                    .contains("Story")
                    .contains("Medium")
                    .contains("Alice")
                    .contains("2024-03-01")
                    .contains("2024-03-10");
        }

        @Test
        @DisplayName("shows 'Unassigned' when assignee is null")
        void showsUnassignedWhenNoAssignee() throws Exception {
            String json = """
                    {
                      "key": "NOVA-2",
                      "fields": {
                        "summary": "Unassigned task",
                        "status":    { "name": "Open" },
                        "issuetype": { "name": "Task" },
                        "priority":  { "name": "Low" }
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).contains("Unassigned");
        }

        @Test
        @DisplayName("includes description when present")
        void includesDescriptionWhenPresent() throws Exception {
            String json = """
                    {
                      "key": "NOVA-3",
                      "fields": {
                        "summary": "With description",
                        "description": "This is the full description of the issue."
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Description")
                    .contains("This is the full description of the issue.");
        }

        @Test
        @DisplayName("omits description section when description is blank")
        void omitsDescriptionSectionWhenBlank() throws Exception {
            String json = """
                    {
                      "key": "NOVA-4",
                      "fields": {
                        "summary": "No description",
                        "description": ""
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).doesNotContain("**Description:**");
        }

        @Test
        @DisplayName("includes last 3 comments when comments are present")
        void includesLast3Comments() throws Exception {
            String json = """
                    {
                      "key": "NOVA-5",
                      "fields": {
                        "summary": "Commented issue",
                        "comment": {
                          "comments": [
                            { "author": { "displayName": "Alice" }, "body": "First comment" },
                            { "author": { "displayName": "Bob" },   "body": "Second comment" },
                            { "author": { "displayName": "Carol" }, "body": "Third comment" },
                            { "author": { "displayName": "Dave" },  "body": "Fourth comment" }
                          ]
                        }
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            // Only last 3 should be shown (Bob, Carol, Dave – not Alice)
            assertThat(result.getText())
                    .contains("Bob")
                    .contains("Carol")
                    .contains("Dave")
                    .doesNotContain("First comment");
        }

        @Test
        @DisplayName("shows correct comment count header when fewer than 3 comments")
        void showsCorrectCommentCountHeader() throws Exception {
            String json = """
                    {
                      "key": "NOVA-6",
                      "fields": {
                        "summary": "Two comments",
                        "comment": {
                          "comments": [
                            { "author": { "displayName": "Alice" }, "body": "Alpha" },
                            { "author": { "displayName": "Bob" },   "body": "Beta" }
                          ]
                        }
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).contains("Last 2 comment(s)");
        }

        @Test
        @DisplayName("includes outward linked issues")
        void includesOutwardLinkedIssues() throws Exception {
            String json = """
                    {
                      "key": "NOVA-7",
                      "fields": {
                        "summary": "Has links",
                        "issuelinks": [
                          {
                            "type": { "name": "Blocks", "outward": "blocks" },
                            "outwardIssue": {
                              "key": "NOVA-8",
                              "fields": {
                                "summary": "Blocked issue",
                                "status": { "name": "Open" }
                              }
                            }
                          }
                        ]
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Linked Issues")
                    .contains("NOVA-8")
                    .contains("blocks")
                    .contains("Blocked issue");
        }

        @Test
        @DisplayName("includes inward linked issues")
        void includesInwardLinkedIssues() throws Exception {
            String json = """
                    {
                      "key": "NOVA-9",
                      "fields": {
                        "summary": "Has inward link",
                        "issuelinks": [
                          {
                            "type": { "name": "Blocks", "inward": "is blocked by" },
                            "inwardIssue": {
                              "key": "NOVA-10",
                              "fields": {
                                "summary": "Blocking issue",
                                "status": { "name": "In Progress" }
                              }
                            }
                          }
                        ]
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("NOVA-10")
                    .contains("is blocked by");
        }

        @Test
        @DisplayName("includes subtasks when present")
        void includesSubtasks() throws Exception {
            String json = """
                    {
                      "key": "NOVA-11",
                      "fields": {
                        "summary": "Parent issue",
                        "subtasks": [
                          {
                            "key": "NOVA-12",
                            "fields": { "summary": "Child task", "status": { "name": "Done" } }
                          }
                        ]
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Sub-Tasks")
                    .contains("NOVA-12")
                    .contains("Child task")
                    .contains("Done");
        }

        @Test
        @DisplayName("includes attachments with filename, size, mimeType and URL")
        void includesAttachments() throws Exception {
            String json = """
                    {
                      "key": "NOVA-13",
                      "fields": {
                        "summary": "With attachment",
                        "attachment": [
                          {
                            "filename": "report.pdf",
                            "mimeType": "application/pdf",
                            "size": 20480,
                            "content": "https://jira.example.com/attachments/report.pdf"
                          }
                        ]
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Attachments")
                    .contains("report.pdf")
                    .contains("application/pdf")
                    .contains("https://jira.example.com/attachments/report.pdf")
                    .contains("read_jira_attachment");
        }

        @Test
        @DisplayName("does not include attachment section when no attachments")
        void omitsAttachmentSectionWhenEmpty() throws Exception {
            String json = """
                    {
                      "key": "NOVA-14",
                      "fields": {
                        "summary": "No attachments",
                        "attachment": []
                      }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).doesNotContain("Attachments");
        }

        @Test
        @DisplayName("result has no QueryResult (single-issue is not tabular)")
        void hasNoQueryResult() throws Exception {
            String json = """
                    {
                      "key": "NOVA-15",
                      "fields": { "summary": "Simple" }
                    }""";

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.hasQueryResult()).isFalse();
        }
    }

    // ── formatSearchResponse ──────────────────────────────────────────────────

    @Nested
    @DisplayName("formatSearchResponse()")
    class FormatSearchResponse {

        @Test
        @DisplayName("returns no-issues message when issues array is empty")
        void returnsNoIssuesMessageWhenEmpty() throws Exception {
            String json = """
                    { "total": 0, "issues": [] }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getText()).isNotBlank();
            assertThat(result.hasQueryResult()).isFalse();
        }

        @Test
        @DisplayName("returns no-issues message when issues node is missing")
        void returnsNoIssuesMessageWhenMissing() throws Exception {
            String json = """
                    { "total": 0 }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getText()).isNotBlank();
            assertThat(result.hasQueryResult()).isFalse();
        }

        @Test
        @DisplayName("includes issue key in result text for search with one result")
        void includesIssueKeyInText() throws Exception {
            String json = """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "NOVA-42",
                          "fields": {
                            "summary":   "Important fix",
                            "status":    { "name": "Open" },
                            "assignee":  { "displayName": "Alice" },
                            "priority":  { "name": "High" },
                            "issuetype": { "name": "Bug" }
                          }
                        }
                      ]
                    }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getText()).contains("NOVA-42").contains("Important fix");
        }

        @Test
        @DisplayName("result has QueryResult with correct columns")
        void hasQueryResultWithCorrectColumns() throws Exception {
            String json = """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "NOVA-1",
                          "fields": {
                            "summary": "Test",
                            "status":    { "name": "Open" },
                            "assignee":  { "displayName": "Alice" },
                            "priority":  { "name": "Medium" },
                            "issuetype": { "name": "Task" }
                          }
                        }
                      ]
                    }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.hasQueryResult()).isTrue();
            assertThat(result.getQueryResult().getColumns())
                    .containsExactly("KEY", "SUMMARY", "STATUS", "ASSIGNEE", "PRIORITY", "TYPE");
        }

        @Test
        @DisplayName("QueryResult rows contain correct data for each issue")
        void queryResultRowsContainCorrectData() throws Exception {
            String json = """
                    {
                      "total": 2,
                      "issues": [
                        {
                          "key": "NOVA-1",
                          "fields": {
                            "summary": "First",
                            "status":    { "name": "Open" },
                            "assignee":  { "displayName": "Alice" },
                            "priority":  { "name": "High" },
                            "issuetype": { "name": "Bug" }
                          }
                        },
                        {
                          "key": "NOVA-2",
                          "fields": {
                            "summary": "Second",
                            "status":    { "name": "Done" },
                            "priority":  { "name": "Low" },
                            "issuetype": { "name": "Story" }
                          }
                        }
                      ]
                    }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getQueryResult().getRows()).hasSize(2);
            assertThat(result.getQueryResult().getRows().getFirst()).containsEntry("KEY", "NOVA-1");
            assertThat(result.getQueryResult().getRows().get(1)).containsEntry("KEY", "NOVA-2");
        }

        @Test
        @DisplayName("shows 'Unassigned' for issues without assignee field")
        void showsUnassignedForMissingAssignee() throws Exception {
            String json = """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "NOVA-99",
                          "fields": {
                            "summary": "No assignee",
                            "status": { "name": "Open" }
                          }
                        }
                      ]
                    }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getQueryResult().getRows().get(0))
                    .containsEntry("ASSIGNEE", "Unassigned");
        }

        @Test
        @DisplayName("QueryResult source name is 'Jira'")
        void queryResultSourceNameIsJira() throws Exception {
            String json = """
                    {
                      "total": 1,
                      "issues": [
                        {
                          "key": "NOVA-1",
                          "fields": { "summary": "Test", "status": { "name": "Open" } }
                        }
                      ]
                    }""";

            ToolResult result = formatter.formatSearchResponse(json);

            assertThat(result.getQueryResult().getSourceName()).isEqualTo("Jira");
        }
    }
}



