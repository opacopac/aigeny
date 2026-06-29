package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm_tool.jira.JiraTokenContext;
import com.tschanz.aigeny.llm_tool.jira.JiraWriteContext;
import com.tschanz.aigeny.llm_tool.jira.PendingJiraActionContext;
import org.springframework.stereotype.Service;

/**
 * {@link ContextProvider} for Jira integrations.
 *
 * <p>Manages the following ThreadLocal contexts per request:
 * <ul>
 *   <li>{@link JiraTokenContext} – API authentication token</li>
 *   <li>{@link JiraWriteContext} – write-mode flag</li>
 *   <li>{@link PendingJiraActionContext} – pending write actions (cleared on every setup)</li>
 * </ul>
 */
@Service
public class JiraContextProvider implements ContextProvider {

    /** Token-map key used in {@code Map<String, String> tokens}. */
    public static final String KEY = "jira";

    @Override
    public String getKey() {
        return KEY;
    }

    @Override
    public void setup(String token, boolean writeEnabled) {
        JiraTokenContext.set(token);
        JiraWriteContext.set(writeEnabled);
        PendingJiraActionContext.clear();   // discard any state left from a previous request
    }

    @Override
    public void cleanup() {
        JiraTokenContext.clear();
        JiraWriteContext.clear();
        PendingJiraActionContext.clear();
    }
}

