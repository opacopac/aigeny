package com.tschanz.aigeny.llm_tool.jira;

import com.tschanz.aigeny.llm_tool.ToolResult;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link ConfirmationService} that delegates to the
 * ThreadLocal-backed {@link ConfirmationContext}.
 *
 * <p>This implementation is active in production.  Write tools receive it via
 * constructor injection.  Tests use a Mockito mock instead, which allows testing
 * the confirmation flow without a running SSE streaming context.
 */
@Service
public class ThreadLocalConfirmationService implements ConfirmationService {

    @Override
    public boolean isAvailable() {
        return ConfirmationContext.isAvailable();
    }

    @Override
    public ToolResult requestConfirmation(String humanDescription, PendingJiraAction action) {
        return ConfirmationContext.get().requestConfirmation(humanDescription, action);
    }
}

