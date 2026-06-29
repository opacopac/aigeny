package com.tschanz.aigeny.confirmation;
import com.tschanz.aigeny.orchestration.WriteToolCallInfo;

import java.util.List;
import java.util.Map;

/**
 * Abstraction over the batch confirmation flow used by {@link OrchestrationService}.
 *
 * <p>Replaces the previous direct access to the static {@link BatchConfirmationContext}
 * and {@code ConfirmationContext.setPreApprovedDecisions()} calls.  This makes
 * {@code OrchestrationService} independent of the concrete ThreadLocal implementation,
 * enabling full unit-test isolation via a mock.
 *
 * <p>The default production implementation is {@link ThreadLocalBatchConfirmationService}.
 */
public interface BatchConfirmationService {

    /**
     * Returns {@code true} if a batch confirmation handler is registered for the current thread.
     * When {@code false}, the pre-scan step in {@code OrchestrationService} is skipped entirely.
     */
    boolean isAvailable();

    /**
     * Requests a combined confirmation dialog for all {@code writeToolInfos} in one LLM response.
     * Blocks until the user submits all decisions.
     *
     * @param writeToolInfos the write tool calls that require confirmation
     * @return map of {@code toolCallId → confirmed}
     */
    Map<String, Boolean> requestBatchConfirmation(List<WriteToolCallInfo> writeToolInfos);

    /**
     * Stores per-tool-call decisions so that individual write tools can skip their own
     * blocking confirmation dialog and use the pre-stored decision directly.
     *
     * @param decisions map of {@code toolCallId → confirmed}
     */
    void applyPreApprovedDecisions(Map<String, Boolean> decisions);
}

