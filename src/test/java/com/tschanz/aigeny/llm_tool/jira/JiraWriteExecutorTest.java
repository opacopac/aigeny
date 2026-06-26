package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.config.JiraConfiguration;
import com.tschanz.aigeny.llm_tool.jira.operation.AddCommentOperation;
import com.tschanz.aigeny.llm_tool.jira.operation.CreateIssueOperation;
import com.tschanz.aigeny.llm_tool.jira.operation.UpdateIssueOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JiraWriteExecutor")
class JiraWriteExecutorTest {

    private JiraWriteExecutor executor;

    @Mock private JiraConfiguration jiraConfig;
    @Mock private UpdateIssueOperation updateOperation;
    @Mock private AddCommentOperation commentOperation;
    @Mock private CreateIssueOperation createOperation;

    @BeforeEach
    void setUp() {
        lenient().when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com");
        executor = new JiraWriteExecutor(jiraConfig, updateOperation, commentOperation, createOperation);
        JiraWriteContext.set(true);
    }

    @AfterEach
    void tearDown() {
        JiraWriteContext.clear();
    }

    private PendingJiraAction action(PendingJiraAction.ActionType type, String key) {
        return new PendingJiraAction(type, key, Map.<String, Object>of("summary", "test"), "desc");
    }

    @Test
    @DisplayName("should delegate UPDATE_ISSUE to UpdateIssueOperation")
    void shouldDelegateUpdateIssue() throws Exception {
        PendingJiraAction a = action(PendingJiraAction.ActionType.UPDATE_ISSUE, "TEST-1");
        when(updateOperation.execute(eq(a), anyString(), anyString())).thenReturn("updated");

        String result = executor.execute(a, "test-token");

        assertThat(result).isEqualTo("updated");
        verify(updateOperation).execute(a, "https://jira.example.com", "test-token");
        verifyNoInteractions(commentOperation, createOperation);
    }

    @Test
    @DisplayName("should delegate ADD_COMMENT to AddCommentOperation")
    void shouldDelegateAddComment() throws Exception {
        PendingJiraAction a = action(PendingJiraAction.ActionType.ADD_COMMENT, "TEST-2");
        when(commentOperation.execute(eq(a), anyString(), anyString())).thenReturn("commented");

        String result = executor.execute(a, "test-token");

        assertThat(result).isEqualTo("commented");
        verify(commentOperation).execute(a, "https://jira.example.com", "test-token");
        verifyNoInteractions(updateOperation, createOperation);
    }

    @Test
    @DisplayName("should delegate CREATE_ISSUE to CreateIssueOperation")
    void shouldDelegateCreateIssue() throws Exception {
        PendingJiraAction a = action(PendingJiraAction.ActionType.CREATE_ISSUE, null);
        when(createOperation.execute(eq(a), anyString(), anyString())).thenReturn("created");

        String result = executor.execute(a, "test-token");

        assertThat(result).isEqualTo("created");
        verify(createOperation).execute(a, "https://jira.example.com", "test-token");
        verifyNoInteractions(updateOperation, commentOperation);
    }

    @Test
    @DisplayName("should return disabled message when write mode is off")
    void shouldReturnDisabledMessage() throws Exception {
        JiraWriteContext.set(false);
        PendingJiraAction a = action(PendingJiraAction.ActionType.UPDATE_ISSUE, "TEST-1");

        String result = executor.execute(a, "test-token");

        assertThat(result).contains("DISABLED");
        verifyNoInteractions(updateOperation, commentOperation, createOperation);
    }

    @Test
    @DisplayName("should strip trailing slash from base URL")
    void shouldStripTrailingSlash() throws Exception {
        when(jiraConfig.getBaseUrl()).thenReturn("https://jira.example.com/");
        PendingJiraAction a = action(PendingJiraAction.ActionType.UPDATE_ISSUE, "TEST-1");
        when(updateOperation.execute(eq(a), anyString(), anyString())).thenReturn("ok");

        executor.execute(a, "test-token");

        verify(updateOperation).execute(a, "https://jira.example.com", "test-token");
    }

    @Test
    @DisplayName("should route all action types correctly")
    void shouldRouteAllActionTypes() throws Exception {
        PendingJiraAction u = action(PendingJiraAction.ActionType.UPDATE_ISSUE, "T-1");
        PendingJiraAction c = action(PendingJiraAction.ActionType.ADD_COMMENT, "T-2");
        PendingJiraAction n = action(PendingJiraAction.ActionType.CREATE_ISSUE, null);

        when(updateOperation.execute(any(), anyString(), anyString())).thenReturn("u");
        when(commentOperation.execute(any(), anyString(), anyString())).thenReturn("c");
        when(createOperation.execute(any(), anyString(), anyString())).thenReturn("n");

        executor.execute(u, "t");
        executor.execute(c, "t");
        executor.execute(n, "t");

        verify(updateOperation, times(1)).execute(any(), anyString(), anyString());
        verify(commentOperation, times(1)).execute(any(), anyString(), anyString());
        verify(createOperation, times(1)).execute(any(), anyString(), anyString());
    }
}

