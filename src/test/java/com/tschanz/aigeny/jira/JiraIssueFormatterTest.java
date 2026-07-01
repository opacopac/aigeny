package com.tschanz.aigeny.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JiraIssueFormatter}.
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
        @DisplayName("renders Due Date row when duedate field is present")
        void rendersDueDateWhenPresent() throws Exception {
            String json = """
                    {
                      "key": "NOVA-42",
                      "fields": {
                        "summary": "Fix login bug",
                        "status": {"name": "Open"},
                        "issuetype": {"name": "Bug"},
                        "priority": {"name": "High"},
                        "assignee": {"displayName": "Alice"},
                        "created": "2026-01-01T10:00:00.000+0000",
                        "updated": "2026-06-01T12:00:00.000+0000",
                        "duedate": "2026-07-15",
                        "description": null,
                        "comment": {"comments": []},
                        "attachment": [],
                        "issuelinks": [],
                        "subtasks": []
                      }
                    }
                    """;

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("Due Date")
                    .contains("2026-07-15");
        }

        @Test
        @DisplayName("omits Due Date row when duedate field is null")
        void omitsDueDateWhenNull() throws Exception {
            String json = """
                    {
                      "key": "NOVA-43",
                      "fields": {
                        "summary": "No due date issue",
                        "status": {"name": "Open"},
                        "issuetype": {"name": "Task"},
                        "priority": {"name": "Low"},
                        "assignee": null,
                        "created": "2026-01-01T10:00:00.000+0000",
                        "updated": "2026-06-01T12:00:00.000+0000",
                        "duedate": null,
                        "description": null,
                        "comment": {"comments": []},
                        "attachment": [],
                        "issuelinks": [],
                        "subtasks": []
                      }
                    }
                    """;

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).doesNotContain("Due Date");
        }

        @Test
        @DisplayName("omits Due Date row when duedate field is absent")
        void omitsDueDateWhenAbsent() throws Exception {
            String json = """
                    {
                      "key": "NOVA-44",
                      "fields": {
                        "summary": "Missing duedate field",
                        "status": {"name": "In Progress"},
                        "issuetype": {"name": "Story"},
                        "priority": {"name": "Medium"},
                        "assignee": {"displayName": "Bob"},
                        "created": "2026-02-01T09:00:00.000+0000",
                        "updated": "2026-06-15T08:00:00.000+0000",
                        "description": null,
                        "comment": {"comments": []},
                        "attachment": [],
                        "issuelinks": [],
                        "subtasks": []
                      }
                    }
                    """;

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText()).doesNotContain("Due Date");
        }

        @Test
        @DisplayName("renders core fields regardless of duedate presence")
        void rendersCoreFields() throws Exception {
            String json = """
                    {
                      "key": "NOVA-99",
                      "fields": {
                        "summary": "Core fields test",
                        "status": {"name": "Closed"},
                        "issuetype": {"name": "Bug"},
                        "priority": {"name": "Critical"},
                        "assignee": {"displayName": "Charlie"},
                        "created": "2026-03-01T08:00:00.000+0000",
                        "updated": "2026-03-15T08:00:00.000+0000",
                        "duedate": "2026-04-01",
                        "description": null,
                        "comment": {"comments": []},
                        "attachment": [],
                        "issuelinks": [],
                        "subtasks": []
                      }
                    }
                    """;

            ToolResult result = formatter.formatSingleIssue(json);

            assertThat(result.getText())
                    .contains("NOVA-99")
                    .contains("Core fields test")
                    .contains("Closed")
                    .contains("Bug")
                    .contains("Critical")
                    .contains("Charlie")
                    .contains("Due Date")
                    .contains("2026-04-01");
        }
    }
}

