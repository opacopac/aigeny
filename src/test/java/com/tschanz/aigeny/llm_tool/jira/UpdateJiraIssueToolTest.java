package com.tschanz.aigeny.llm_tool.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm_tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UpdateJiraIssueTool}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The tool uses the injected {@link ConfirmationService} instead of the static
 *       {@link ConfirmationContext} (D-1 fix)</li>
 *   <li>Guard conditions (write-mode disabled, not configured, missing arguments) return
 *       an error without calling the confirmation service</li>
 *   <li>When a valid request is made the tool delegates to
 *       {@link ConfirmationService#requestConfirmation} and returns its result</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateJiraIssueTool")
class UpdateJiraIssueToolTest {

    @Mock private JiraConfiguration jiraConfig;
    @Mock private ConfirmationService confirmationService;

    private UpdateJiraIssueTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new UpdateJiraIssueTool(jiraConfig, objectMapper, confirmationService);
        lenient().when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com");
        JiraWriteContext.set(true);
    }

    @AfterEach
    void tearDown() {
        JiraWriteContext.set(false);
    }

    // ── Guard conditions ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Guard conditions")
    class GuardConditions {

        @Test
        @DisplayName("returns error and does not call confirmation service when write mode disabled")
        void returnsErrorWhenWriteModeDisabled() throws Exception {
            JiraWriteContext.set(false);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"summary\":\"New summary\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when base URL is blank")
        void returnsErrorWhenBaseUrlBlank() throws Exception {
            when(jiraConfig.getBaseUrl()).thenReturn("");

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"summary\":\"New summary\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when issueKey is missing")
        void returnsErrorWhenIssueKeyMissing() throws Exception {
            ToolResult result = tool.execute("{\"summary\":\"New summary\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when neither summary nor description is provided")
        void returnsErrorWhenNoFieldsProvided() throws Exception {
            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when confirmationService.isAvailable() is false")
        void returnsErrorWhenConfirmationServiceNotAvailable() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(false);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"summary\":\"New summary\"}");

            assertThat(result.getText()).isNotBlank();
            verify(confirmationService, never()).requestConfirmation(anyString(), any());
        }
    }

    // ── Injected ConfirmationService (D-1) ────────────────────────────────────

    @Nested
    @DisplayName("Uses injected ConfirmationService (D-1)")
    class UsesInjectedConfirmationService {

        @Test
        @DisplayName("delegates to confirmationService.requestConfirmation() with issueKey and summary")
        void delegatesWithSummary() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult expected = new ToolResult("updated!");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(expected);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-42\",\"summary\":\"Fixed title\"}");

            assertThat(result).isSameAs(expected);
            verify(confirmationService).requestConfirmation(contains("NOVA-42"), any(PendingJiraAction.class));
        }

        @Test
        @DisplayName("delegates to confirmationService.requestConfirmation() with description")
        void delegatesWithDescription() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult expected = new ToolResult("updated!");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(expected);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-42\",\"description\":\"New desc\"}");

            assertThat(result).isSameAs(expected);
            verify(confirmationService).requestConfirmation(
                    contains("NOVA-42"), any(PendingJiraAction.class));
        }

        @Test
        @DisplayName("passes UPDATE_ISSUE action to requestConfirmation")
        void passesUpdateIssueAction() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any()))
                    .thenReturn(new ToolResult("ok"));

            tool.execute("{\"issueKey\":\"NOVA-99\",\"summary\":\"s\"}");

            verify(confirmationService).requestConfirmation(
                    anyString(),
                    argThat(action -> action.getActionType() == PendingJiraAction.ActionType.UPDATE_ISSUE
                            && "NOVA-99".equals(action.getIssueKey())));
        }

        @Test
        @DisplayName("returns the result from confirmationService unchanged")
        void returnsConfirmationServiceResult() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult declined = new ToolResult("Aktion abgelehnt.");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(declined);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"summary\":\"s\"}");

            assertThat(result).isSameAs(declined);
        }
    }
}

