package com.tschanz.aigeny.jira.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.jira.confirmation.ConfirmationService;
import com.tschanz.aigeny.jira.confirmation.JiraWriteContext;
import com.tschanz.aigeny.jira.confirmation.PendingJiraAction;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UpdateJiraIssueTool}.
 */
@DisplayName("UpdateJiraIssueTool")
class UpdateJiraIssueToolTest {

    private static final String BASE_URL  = "https://jira.example.com";
    private static final String ISSUE_KEY = "NOVA-42";

    private JiraConfiguration   jiraConfig;
    private ConfirmationService confirmationService;
    private UpdateJiraIssueTool tool;

    @BeforeEach
    void setUp() throws Exception {
        jiraConfig          = mock(JiraConfiguration.class);
        confirmationService = mock(ConfirmationService.class);

        when(jiraConfig.getBaseUrl()).thenReturn(BASE_URL);
        when(confirmationService.isAvailable()).thenReturn(true);
        when(confirmationService.requestConfirmation(anyString(), any()))
                .thenAnswer(inv -> new ToolResult("queued"));

        tool = new UpdateJiraIssueTool(jiraConfig, new ObjectMapper(), confirmationService);

        // Enable write mode for tests that need it
        JiraWriteContext.set(true);
    }

    @AfterEach
    void tearDown() {
        JiraWriteContext.set(false);
    }

    // ── duedate argument ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("duedate field")
    class DuedateField {

        @Test
        @DisplayName("passes duedate in PendingJiraAction params when provided")
        void passesDuedateInParams() throws Exception {
            ArgumentCaptor<PendingJiraAction> actionCaptor = ArgumentCaptor.forClass(PendingJiraAction.class);

            tool.execute("{\"issueKey\": \"" + ISSUE_KEY + "\", \"duedate\": \"2026-07-31\"}");

            verify(confirmationService).requestConfirmation(anyString(), actionCaptor.capture());
            PendingJiraAction action = actionCaptor.getValue();
            assertThat(action.getParams())
                    .containsKey("duedate")
                    .containsEntry("duedate", "2026-07-31");
        }

        @Test
        @DisplayName("includes Due Date in human description shown to user")
        void includesDuedateInDescription() throws Exception {
            ArgumentCaptor<String> descCaptor = ArgumentCaptor.forClass(String.class);

            tool.execute("{\"issueKey\": \"" + ISSUE_KEY + "\", \"duedate\": \"2026-07-31\"}");

            verify(confirmationService).requestConfirmation(descCaptor.capture(), any());
            assertThat(descCaptor.getValue())
                    .contains("Due Date")
                    .contains("2026-07-31");
        }

        @Test
        @DisplayName("accepts duedate as the only field (no summary or description required)")
        void acceptsDuedateAsOnlyField() throws Exception {
            ToolResult result = tool.execute(
                    "{\"issueKey\": \"" + ISSUE_KEY + "\", \"duedate\": \"2026-08-01\"}");

            // Should NOT return a missing-fields error
            assertThat(result.getText()).doesNotContain("duedate").doesNotContain("summary");
            verify(confirmationService).requestConfirmation(anyString(), any());
        }

        @Test
        @DisplayName("combines duedate with summary and description in params")
        void combinesDuedateWithOtherFields() throws Exception {
            ArgumentCaptor<PendingJiraAction> actionCaptor = ArgumentCaptor.forClass(PendingJiraAction.class);

            tool.execute("{\"issueKey\": \"" + ISSUE_KEY
                    + "\", \"summary\": \"New title\", \"description\": \"New desc\", \"duedate\": \"2026-09-01\"}");

            verify(confirmationService).requestConfirmation(anyString(), actionCaptor.capture());
            assertThat(actionCaptor.getValue().getParams())
                    .containsKey("summary")
                    .containsKey("description")
                    .containsKey("duedate");
        }
    }

    // ── getCallDescription ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCallDescription()")
    class GetCallDescription {

        @Test
        @DisplayName("shows 'Fälligkeitsdatum' when only duedate is provided")
        void showsFaelligkeitsdatumForDuedate() {
            String desc = tool.getCallDescription(
                    "{\"issueKey\": \"NOVA-10\", \"duedate\": \"2026-07-31\"}");
            assertThat(desc).contains("NOVA-10").contains("Fälligkeitsdatum");
        }

        @Test
        @DisplayName("lists all field names when summary, description and duedate are provided")
        void listsAllFieldNames() {
            String desc = tool.getCallDescription(
                    "{\"issueKey\": \"NOVA-10\", \"summary\": \"s\", \"description\": \"d\", \"duedate\": \"2026-07-31\"}");
            assertThat(desc)
                    .contains("Summary")
                    .contains("Beschreibung")
                    .contains("Fälligkeitsdatum");
        }

        @Test
        @DisplayName("falls back to 'Felder' when no updatable field is provided")
        void fallsBackToFelder() {
            String desc = tool.getCallDescription("{\"issueKey\": \"NOVA-10\"}");
            assertThat(desc).contains("Felder");
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("returns missing-fields error when no field is provided")
        void returnsMissingFieldsWhenNothingGiven() throws Exception {
            ToolResult result = tool.execute("{\"issueKey\": \"NOVA-10\"}");
            // Must not proceed to confirmationService
            verify(confirmationService, never()).requestConfirmation(anyString(), any());
            assertThat(result.getText()).isNotBlank();
        }

        @Test
        @DisplayName("returns write-disabled message when JiraWriteContext is off")
        void returnsWriteDisabledWhenContextOff() throws Exception {
            JiraWriteContext.set(false);
            ToolResult result = tool.execute(
                    "{\"issueKey\": \"NOVA-10\", \"duedate\": \"2026-07-31\"}");
            verify(confirmationService, never()).requestConfirmation(anyString(), any());
            assertThat(result.getText()).isNotBlank();
        }
    }
}

