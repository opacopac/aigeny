package com.tschanz.aigeny.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ChatSessionService} correctly implements all five narrow
 * session interfaces introduced by the I-1 refactoring.
 *
 * <p>These are compile-time contracts, but the runtime {@code instanceof} checks
 * serve as living documentation and guard against accidental removal of the
 * {@code implements} clause.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatSessionService – ISP interface contracts (I-1)")
class ChatSessionServiceInterfaceContractTest {

    @Mock private ConfirmationFutureManager confirmationFutureManager;
    @Mock private CancellationManager cancellationManager;
    @Mock private HistoryManager historyManager;

    private ChatSessionService build() {
        return new ChatSessionService(confirmationFutureManager, cancellationManager, historyManager);
    }

    @Test
    @DisplayName("implements SessionHistoryService")
    void implementsSessionHistoryService() {
        assertThat(build()).isInstanceOf(SessionHistoryService.class);
    }

    @Test
    @DisplayName("implements SessionExportService")
    void implementsSessionExportService() {
        assertThat(build()).isInstanceOf(SessionExportService.class);
    }

    @Test
    @DisplayName("implements SessionCancellationService")
    void implementsSessionCancellationService() {
        assertThat(build()).isInstanceOf(SessionCancellationService.class);
    }

    @Test
    @DisplayName("implements SessionConfirmationService")
    void implementsSessionConfirmationService() {
        assertThat(build()).isInstanceOf(SessionConfirmationService.class);
    }

    @Test
    @DisplayName("implements SessionJiraWriteService")
    void implementsSessionJiraWriteService() {
        assertThat(build()).isInstanceOf(SessionJiraWriteService.class);
    }

    @Test
    @DisplayName("is assignable to all five interfaces simultaneously")
    void isAssignableToAllInterfaces() {
        ChatSessionService service = build();

        SessionHistoryService     history      = service;
        SessionExportService      export       = service;
        SessionCancellationService cancellation = service;
        SessionConfirmationService confirmation = service;
        SessionJiraWriteService   jiraWrite    = service;

        assertThat(history).isNotNull();
        assertThat(export).isNotNull();
        assertThat(cancellation).isNotNull();
        assertThat(confirmation).isNotNull();
        assertThat(jiraWrite).isNotNull();
    }
}

