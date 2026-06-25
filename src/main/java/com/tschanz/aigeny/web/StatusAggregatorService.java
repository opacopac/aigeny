package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.db.SchemaLoader;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregates system status information from various sources including
 * LLM configuration, database, Jira, Bitbucket, and schema metadata.
 */
@Service
public class StatusAggregatorService {

    // Status map keys
    private static final String KEY_LLM_PROVIDER                = "llmProvider";
    private static final String KEY_LLM_MODEL                   = "llmModel";
    private static final String KEY_DB_CONFIGURED               = "dbConfigured";
    private static final String KEY_DB_USERNAME                 = "dbUsername";
    private static final String KEY_JIRA_CONFIGURED             = "jiraConfigured";
    private static final String KEY_JIRA_BASEURL_CONFIGURED     = "jiraBaseUrlConfigured";
    private static final String KEY_JIRA_WRITE_ENABLED          = "jiraWriteEnabled";
    private static final String KEY_BITBUCKET_CONFIGURED        = "bitbucketConfigured";
    private static final String KEY_BITBUCKET_BASEURL_CONFIGURED = "bitbucketBaseUrlConfigured";
    private static final String KEY_SCHEMA_TABLES               = "schemaTables";
    private static final String KEY_HAS_EXPORT                  = "hasExport";

    private final AigenyProperties props;
    private final TokenService tokenService;
    private final ChatSessionService sessionService;
    private final SchemaLoader schemaLoader;

    public StatusAggregatorService(AigenyProperties props,
                                   TokenService tokenService,
                                   ChatSessionService sessionService,
                                   SchemaLoader schemaLoader) {
        this.props = props;
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.schemaLoader = schemaLoader;
    }

    /**
     * Aggregates the complete system status including configuration state,
     * token availability, and data availability for the current session.
     *
     * @param session HTTP session
     * @return map containing all status information
     */
    public Map<String, Object> aggregateStatus(HttpSession session) {
        Map<String, Object> status = new HashMap<>();

        // LLM configuration
        status.put(KEY_LLM_PROVIDER, props.getLlm().getProvider());
        status.put(KEY_LLM_MODEL, props.getLlm().getModel());

        // Database configuration
        status.put(KEY_DB_CONFIGURED, props.isDbConfigured());
        status.put(KEY_DB_USERNAME, props.getDb().getUsername());
        status.put(KEY_SCHEMA_TABLES, schemaLoader.getTableCount());

        // Jira configuration and session state
        status.put(KEY_JIRA_CONFIGURED, tokenService.hasJiraToken(session));
        status.put(KEY_JIRA_BASEURL_CONFIGURED, isJiraBaseUrlConfigured());
        status.put(KEY_JIRA_WRITE_ENABLED, sessionService.isJiraWriteModeEnabled(session));

        // Bitbucket configuration
        status.put(KEY_BITBUCKET_CONFIGURED, tokenService.hasBitbucketToken(session));
        status.put(KEY_BITBUCKET_BASEURL_CONFIGURED, isBitbucketBaseUrlConfigured());

        // Export data availability
        status.put(KEY_HAS_EXPORT, sessionService.hasQueryResult(session));

        return status;
    }

    /**
     * Checks if the Jira base URL is configured in application properties.
     *
     * @return true if Jira base URL is set and not blank
     */
    public boolean isJiraBaseUrlConfigured() {
        String baseUrl = props.getJira().getBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * Checks if the Bitbucket base URL is configured in application properties.
     *
     * @return true if Bitbucket base URL is set and not blank
     */
    public boolean isBitbucketBaseUrlConfigured() {
        String baseUrl = props.getBitbucket().getBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }
}

