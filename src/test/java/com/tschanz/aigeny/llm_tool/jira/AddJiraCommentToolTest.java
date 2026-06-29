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
 * Unit tests for {@link AddJiraCommentTool}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>The tool uses the injected {@link ConfirmationService} instead of the static
 *       {@link ConfirmationContext} (D-1 fix)</li>
 *   <li>Guard conditions return an error without calling the confirmation service</li>
 *   <li>When a valid request is made the tool delegates to
 *       {@link ConfirmationService#requestConfirmation} with the correct action</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddJiraCommentTool")
class AddJiraCommentToolTest {

    @Mock private JiraConfiguration jiraConfig;
    @Mock private ConfirmationService confirmationService;

    private AddJiraCommentTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tool = new AddJiraCommentTool(jiraConfig, objectMapper, confirmationService);
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

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"comment\":\"Nice work!\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when base URL is blank")
        void returnsErrorWhenBaseUrlBlank() throws Exception {
            when(jiraConfig.getBaseUrl()).thenReturn("");

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"comment\":\"comment\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when issueKey is missing")
        void returnsErrorWhenIssueKeyMissing() throws Exception {
            ToolResult result = tool.execute("{\"comment\":\"comment\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when comment is blank")
        void returnsErrorWhenCommentBlank() throws Exception {
            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"comment\":\"\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(confirmationService);
        }

        @Test
        @DisplayName("returns error when confirmationService.isAvailable() is false")
        void returnsErrorWhenConfirmationServiceNotAvailable() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(false);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"comment\":\"Nice!\"}");

            assertThat(result.getText()).isNotBlank();
            verify(confirmationService, never()).requestConfirmation(anyString(), any());
        }
    }

    // ── Injected ConfirmationService (D-1) ────────────────────────────────────

    @Nested
    @DisplayName("Uses injected ConfirmationService (D-1)")
    class UsesInjectedConfirmationService {

        @Test
        @DisplayName("delegates to confirmationService.requestConfirmation() with issueKey")
        void delegatesWithIssueKey() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult expected = new ToolResult("comment added!");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(expected);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-77\",\"comment\":\"LGTM\"}");

            assertThat(result).isSameAs(expected);
            verify(confirmationService).requestConfirmation(contains("NOVA-77"), any(PendingJiraAction.class));
        }

        @Test
        @DisplayName("passes ADD_COMMENT action with correct issueKey to requestConfirmation")
        void passesAddCommentAction() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any()))
                    .thenReturn(new ToolResult("ok"));

            tool.execute("{\"issueKey\":\"NOVA-5\",\"comment\":\"Done\"}");

            verify(confirmationService).requestConfirmation(
                    anyString(),
                    argThat(action -> action.getActionType() == PendingJiraAction.ActionType.ADD_COMMENT
                            && "NOVA-5".equals(action.getIssueKey())));
        }

        @Test
        @DisplayName("includes comment text in action params")
        void includesCommentInActionParams() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            when(confirmationService.requestConfirmation(anyString(), any()))
                    .thenReturn(new ToolResult("ok"));

            tool.execute("{\"issueKey\":\"NOVA-5\",\"comment\":\"My comment\"}");

            verify(confirmationService).requestConfirmation(
                    anyString(),
                    argThat(action -> "My comment".equals(action.getParams().get("comment"))));
        }

        @Test
        @DisplayName("returns the result from confirmationService unchanged")
        void returnsConfirmationServiceResult() throws Exception {
            when(confirmationService.isAvailable()).thenReturn(true);
            ToolResult declined = new ToolResult("Aktion abgelehnt.");
            when(confirmationService.requestConfirmation(anyString(), any())).thenReturn(declined);

            ToolResult result = tool.execute("{\"issueKey\":\"NOVA-1\",\"comment\":\"c\"}");

            assertThat(result).isSameAs(declined);
        }
    }
}

