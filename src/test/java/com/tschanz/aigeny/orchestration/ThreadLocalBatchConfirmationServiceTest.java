package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.tool.jira.ConfirmationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ThreadLocalBatchConfirmationService}.
 *
 * <p>Verifies that the service correctly delegates to the underlying
 * {@link BatchConfirmationContext} and {@link ConfirmationContext} ThreadLocals.
 */
@DisplayName("ThreadLocalBatchConfirmationService")
class ThreadLocalBatchConfirmationServiceTest {

    private ThreadLocalBatchConfirmationService service;

    @BeforeEach
    void setUp() {
        service = new ThreadLocalBatchConfirmationService();
    }

    @AfterEach
    void tearDown() {
        // Ensure clean state for subsequent tests
        BatchConfirmationContext.clear();
        ConfirmationContext.clearPreApproved();
    }

    // ── isAvailable ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        @DisplayName("returns false when no handler is registered")
        void returnsFalse_whenNoHandlerRegistered() {
            assertThat(service.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("returns true after a handler has been set in BatchConfirmationContext")
        void returnsTrue_whenHandlerIsRegistered() {
            BatchConfirmationContext.set(infos -> Map.of());

            assertThat(service.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("returns false again after BatchConfirmationContext is cleared")
        void returnsFalse_afterContextIsCleared() {
            BatchConfirmationContext.set(infos -> Map.of());
            BatchConfirmationContext.clear();

            assertThat(service.isAvailable()).isFalse();
        }
    }

    // ── requestBatchConfirmation ──────────────────────────────────────────────

    @Nested
    @DisplayName("requestBatchConfirmation")
    class RequestBatchConfirmation {

        @Test
        @DisplayName("invokes the registered handler and returns its result")
        void invokesHandler_andReturnsDecisions() {
            Map<String, Boolean> expectedDecisions = Map.of("call-1", true, "call-2", false);
            BatchConfirmationContext.set(infos -> expectedDecisions);

            List<WriteToolCallInfo> toolInfos = List.of(
                    new WriteToolCallInfo("call-1", "create_jira_issue", "Create PROJ-1"),
                    new WriteToolCallInfo("call-2", "update_jira_issue", "Update PROJ-2")
            );

            Map<String, Boolean> result = service.requestBatchConfirmation(toolInfos);

            assertThat(result).isEqualTo(expectedDecisions);
        }

        @Test
        @DisplayName("passes the WriteToolCallInfo list to the handler unchanged")
        void passesToolInfosToHandler_unchanged() {
            List<WriteToolCallInfo> capturedInfos = new java.util.ArrayList<>();
            BatchConfirmationContext.set(infos -> {
                capturedInfos.addAll(infos);
                return Map.of();
            });

            WriteToolCallInfo info1 = new WriteToolCallInfo("call-x", "clone_issue", "Clone ABC-1");
            WriteToolCallInfo info2 = new WriteToolCallInfo("call-y", "create_issue", "Create ABC-2");
            List<WriteToolCallInfo> input = List.of(info1, info2);

            service.requestBatchConfirmation(input);

            assertThat(capturedInfos).containsExactly(info1, info2);
        }

        @Test
        @DisplayName("throws NullPointerException when no handler is registered (no silent swallowing)")
        void throwsNpe_whenNoHandlerRegistered() {
            // No handler set – BatchConfirmationContext.get() returns null,
            // calling apply() on null throws NPE. This is expected behaviour:
            // callers must guard with isAvailable() before calling this method.
            assertThatThrownBy(() ->
                    service.requestBatchConfirmation(List.of(
                            new WriteToolCallInfo("call-1", "create_issue", "desc")
                    ))
            ).isInstanceOf(NullPointerException.class);
        }
    }

    // ── applyPreApprovedDecisions ─────────────────────────────────────────────

    @Nested
    @DisplayName("applyPreApprovedDecisions")
    class ApplyPreApprovedDecisions {

        @Test
        @DisplayName("stores decisions so ConfirmationContext.hasPreApproved returns true")
        void storesDecisions_inConfirmationContext() {
            Map<String, Boolean> decisions = Map.of("call-1", true, "call-2", false);

            service.applyPreApprovedDecisions(decisions);

            assertThat(ConfirmationContext.hasPreApproved("call-1")).isTrue();
            assertThat(ConfirmationContext.hasPreApproved("call-2")).isTrue();
        }

        @Test
        @DisplayName("confirmed decisions return true via ConfirmationContext.getPreApproved")
        void confirmedDecision_returnsTrueViaContext() {
            service.applyPreApprovedDecisions(Map.of("call-confirmed", true));

            assertThat(ConfirmationContext.getPreApproved("call-confirmed")).isTrue();
        }

        @Test
        @DisplayName("declined decisions return false via ConfirmationContext.getPreApproved")
        void declinedDecision_returnsFalseViaContext() {
            service.applyPreApprovedDecisions(Map.of("call-declined", false));

            assertThat(ConfirmationContext.getPreApproved("call-declined")).isFalse();
        }

        @Test
        @DisplayName("unknown toolCallId returns false from ConfirmationContext.getPreApproved")
        void unknownToolCallId_returnsFalseFromContext() {
            service.applyPreApprovedDecisions(Map.of("call-known", true));

            assertThat(ConfirmationContext.getPreApproved("call-unknown")).isFalse();
        }

        @Test
        @DisplayName("can be called with an empty map without side effects")
        void emptyDecisions_causesNoErrors() {
            service.applyPreApprovedDecisions(Map.of());

            assertThat(ConfirmationContext.hasPreApproved("any-id")).isFalse();
        }
    }
}

