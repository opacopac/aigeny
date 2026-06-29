package com.tschanz.aigeny.jira;

import com.tschanz.aigeny.jira.JiraTokenContext;
import com.tschanz.aigeny.jira.confirmation.JiraWriteContext;
import com.tschanz.aigeny.jira.confirmation.PendingJiraAction;
import com.tschanz.aigeny.jira.confirmation.PendingJiraActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link JiraContextProvider}.
 */
@DisplayName("JiraContextProvider")
class JiraContextProviderTest {

    private JiraContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JiraContextProvider();
    }

    @AfterEach
    void tearDown() {
        provider.cleanup();
    }

    @Test
    @DisplayName("getKey() returns 'jira'")
    void getKey_returnsJira() {
        assertThat(provider.getKey()).isEqualTo("jira");
        assertThat(provider.getKey()).isEqualTo(JiraContextProvider.KEY);
    }

    // ── setup ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setup()")
    class Setup {

        @Test
        @DisplayName("sets JiraTokenContext to provided token")
        void setsJiraToken() {
            provider.setup("jira-token-abc", false);

            assertThat(JiraTokenContext.get()).isEqualTo("jira-token-abc");
        }

        @Test
        @DisplayName("sets JiraTokenContext to null when token is null")
        void setsNullToken() {
            provider.setup(null, false);

            assertThat(JiraTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("enables JiraWriteContext when writeEnabled=true")
        void enablesWriteMode() {
            provider.setup("token", true);

            assertThat(JiraWriteContext.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("disables JiraWriteContext when writeEnabled=false")
        void disablesWriteMode() {
            provider.setup("token", false);

            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("clears PendingJiraActionContext on setup (discards stale state)")
        void clearsPendingJiraActionsOnSetup() {
            // Pre-populate stale state
            PendingJiraActionContext.add(mock(PendingJiraAction.class));
            assertThat(PendingJiraActionContext.get()).isNotEmpty();

            provider.setup("token", false);

            assertThat(PendingJiraActionContext.get()).isNullOrEmpty();
        }

        @Test
        @DisplayName("sets all three contexts in a single call")
        void setsAllContexts() {
            provider.setup("my-token", true);

            assertThat(JiraTokenContext.get()).isEqualTo("my-token");
            assertThat(JiraWriteContext.isEnabled()).isTrue();
            assertThat(PendingJiraActionContext.get()).isNullOrEmpty();
        }
    }

    // ── cleanup ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cleanup()")
    class Cleanup {

        @Test
        @DisplayName("clears JiraTokenContext")
        void clearsJiraToken() {
            JiraTokenContext.set("some-token");

            provider.cleanup();

            assertThat(JiraTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("clears JiraWriteContext")
        void clearsJiraWriteContext() {
            JiraWriteContext.set(true);

            provider.cleanup();

            assertThat(JiraWriteContext.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("clears PendingJiraActionContext")
        void clearsPendingJiraActions() {
            PendingJiraActionContext.add(mock(PendingJiraAction.class));

            provider.cleanup();

            assertThat(PendingJiraActionContext.get()).isNull();
        }

        @Test
        @DisplayName("clears all three contexts after a full setup")
        void clearsAllContextsAfterSetup() {
            provider.setup("token", true);
            PendingJiraActionContext.add(mock(PendingJiraAction.class));

            provider.cleanup();

            assertThat(JiraTokenContext.get()).isNull();
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            assertThat(PendingJiraActionContext.get()).isNull();
        }

        @Test
        @DisplayName("cleanup is idempotent – calling twice does not throw")
        void cleanupIsIdempotent() {
            provider.setup("token", true);
            provider.cleanup();
            provider.cleanup(); // should not throw
        }

        @Test
        @DisplayName("cleanup on already-clean state does not throw")
        void cleanupOnCleanState() {
            provider.cleanup(); // nothing was set – should not throw
        }
    }

    // ── setup → cleanup cycle ─────────────────────────────────────────────────

    @Nested
    @DisplayName("setup-cleanup cycle")
    class Cycle {

        @Test
        @DisplayName("supports multiple setup-cleanup cycles")
        void supportsMultipleCycles() {
            provider.setup("token-1", true);
            assertThat(JiraTokenContext.get()).isEqualTo("token-1");
            provider.cleanup();

            provider.setup("token-2", false);
            assertThat(JiraTokenContext.get()).isEqualTo("token-2");
            assertThat(JiraWriteContext.isEnabled()).isFalse();
            provider.cleanup();

            assertThat(JiraTokenContext.get()).isNull();
        }
    }
}

