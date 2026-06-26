package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import com.tschanz.aigeny.llm_tool.jira.ConfirmationContext;
import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import com.tschanz.aigeny.orchestration.BatchConfirmationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages ThreadLocal execution contexts for chat stream processing.
 * 
 * <p>This service centralizes all ThreadLocal context setup and cleanup to ensure:
 * <ul>
 *   <li>Consistent context initialization before orchestration</li>
 *   <li>Proper cleanup in finally blocks to prevent memory leaks</li>
 *   <li>Single point of control for context lifecycle</li>
 *   <li>Easier testing by isolating ThreadLocal management</li>
 * </ul>
 * 
 * <p>ThreadLocal contexts used:
 * <ul>
 *   <li>{@link JiraTokenContext} - Jira API authentication token</li>
 *   <li>{@link JiraWriteContext} - Jira write operations enabled flag</li>
 *   <li>{@link BitbucketTokenContext} - Bitbucket API authentication token</li>
 *   <li>{@link PendingJiraActionContext} - Pending Jira actions for batch processing</li>
 *   <li>{@link ConfirmationContext} - Confirmation handler and pre-approved decisions</li>
 *   <li>{@link BatchConfirmationContext} - Batch confirmation handler</li>
 * </ul>
 * 
 * <p>Extracted from {@link ChatStreamingService} to improve single responsibility principle.
 */
@Service
public class ExecutionContextManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionContextManager.class);

    /**
     * Sets up all ThreadLocal contexts required for chat stream processing.
     * 
     * <p>This method must be called before orchestration begins and should always
     * be paired with {@link #cleanupAllContexts()} in a finally block.
     * 
     * @param jiraToken                Jira API token for authentication
     * @param jiraWriteEnabled         whether Jira write operations are enabled
     * @param bitbucketToken           Bitbucket API token for authentication
     * @param confirmationHandler      handler for single write tool confirmations
     * @param batchConfirmationHandler handler for batch write tool confirmations
     */
    public void setupContexts(String jiraToken,
                             boolean jiraWriteEnabled,
                             String bitbucketToken,
                             ConfirmationContext.Handler confirmationHandler,
                             java.util.function.Function<java.util.List<com.tschanz.aigeny.orchestration.WriteToolCallInfo>, 
                                                         java.util.Map<String, Boolean>> batchConfirmationHandler) {
        log.debug("Setting up execution contexts (jiraWrite={}, hasJiraToken={}, hasBitbucketToken={})",
                jiraWriteEnabled, jiraToken != null, bitbucketToken != null);

        // Setup token contexts
        JiraTokenContext.set(jiraToken);
        JiraWriteContext.set(jiraWriteEnabled);
        BitbucketTokenContext.set(bitbucketToken);

        // Clear any pending state from previous executions
        PendingJiraActionContext.clear();

        // Setup confirmation handlers
        ConfirmationContext.set(confirmationHandler);
        BatchConfirmationContext.set(batchConfirmationHandler);
    }

    /**
     * Clears all ThreadLocal contexts to prevent memory leaks.
     * 
     * <p>This method should always be called in a finally block after
     * {@link #setupContexts} to ensure contexts are cleaned up even if
     * exceptions occur during orchestration.
     * 
     * <p>Also clears pre-approved confirmation decisions that may have been
     * stored during batch confirmation processing.
     */
    public void cleanupAllContexts() {
        log.debug("Cleaning up all execution contexts");

        JiraTokenContext.clear();
        JiraWriteContext.clear();
        BitbucketTokenContext.clear();
        PendingJiraActionContext.clear();
        ConfirmationContext.clear();
        ConfirmationContext.clearPreApproved();
        BatchConfirmationContext.clear();
    }
}
