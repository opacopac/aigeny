package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.llm_tool.jira.ConfirmationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ThreadLocal-backed production implementation of {@link BatchConfirmationService}.
 *
 * <p>Delegates to {@link BatchConfirmationContext} for the batch handler and to
 * {@link ConfirmationContext#setPreApprovedDecisions} for storing per-tool decisions.
 * Both ThreadLocals are populated by {@code ExecutionContextManager.setupContexts()}
 * before each request and cleared by {@code ExecutionContextManager.cleanupAllContexts()}
 * afterwards – this class does not own the lifecycle of those ThreadLocals.
 *
 * <p>Registered as a Spring {@code @Service} so it is auto-wired into
 * {@link OrchestrationService}.
 */
@Service
public class ThreadLocalBatchConfirmationService implements BatchConfirmationService {

    @Override
    public boolean isAvailable() {
        return BatchConfirmationContext.isAvailable();
    }

    @Override
    public Map<String, Boolean> requestBatchConfirmation(List<WriteToolCallInfo> writeToolInfos) {
        return BatchConfirmationContext.get().apply(writeToolInfos);
    }

    @Override
    public void applyPreApprovedDecisions(Map<String, Boolean> decisions) {
        ConfirmationContext.setPreApprovedDecisions(decisions);
    }
}

