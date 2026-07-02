package com.tschanz.aigeny.chat;
import com.tschanz.aigeny.llm.github.TokenService;
import com.tschanz.aigeny.jira.SessionJiraWriteService;
import com.tschanz.aigeny.export.SessionExportService;

import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;
import com.tschanz.aigeny.database.SchemaLoader;
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
    private static final String KEY_DB_REACHABLE                = "dbReachable";
    private static final String KEY_DB_ERROR                    = "dbError";
    private static final String KEY_JIRA_CONFIGURED             = "jiraConfigured";
    private static final String KEY_JIRA_BASEURL_CONFIGURED     = "jiraBaseUrlConfigured";
    private static final String KEY_JIRA_WRITE_ENABLED          = "jiraWriteEnabled";
    private static final String KEY_BITBUCKET_CONFIGURED        = "bitbucketConfigured";
    private static final String KEY_BITBUCKET_BASEURL_CONFIGURED = "bitbucketBaseUrlConfigured";
    private static final String KEY_SCHEMA_TABLES               = "schemaTables";
    private static final String KEY_HAS_EXPORT                  = "hasExport";

    private final LlmConfiguration llmConfig;
    private final DbConfiguration dbConfig;
    private final JiraConfiguration jiraConfig;
    private final BitbucketConfiguration bitbucketConfig;
    private final ConfigurationValidator configValidator;
    private final TokenService tokenService;
    private final SessionJiraWriteService jiraWriteService;
    private final SessionExportService exportService;
    private final SchemaLoader schemaLoader;

    public StatusAggregatorService(LlmConfiguration llmConfig,
                                   DbConfiguration dbConfig,
                                   JiraConfiguration jiraConfig,
                                   BitbucketConfiguration bitbucketConfig,
                                   ConfigurationValidator configValidator,
                                   TokenService tokenService,
                                   SessionJiraWriteService jiraWriteService,
                                   SessionExportService exportService,
                                   SchemaLoader schemaLoader) {
        this.llmConfig = llmConfig;
        this.dbConfig = dbConfig;
        this.jiraConfig = jiraConfig;
        this.bitbucketConfig = bitbucketConfig;
        this.configValidator = configValidator;
        this.tokenService = tokenService;
        this.jiraWriteService = jiraWriteService;
        this.exportService = exportService;
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
        status.put(KEY_LLM_PROVIDER, llmConfig.getProvider());
        status.put(KEY_LLM_MODEL, llmConfig.getModel());

        // Database configuration
        status.put(KEY_DB_CONFIGURED, configValidator.isDbConfigured(dbConfig));
        status.put(KEY_DB_USERNAME, dbConfig.getUsername());
        status.put(KEY_DB_REACHABLE, schemaLoader.isDbReachable());
        status.put(KEY_DB_ERROR, schemaLoader.getLastError());
        status.put(KEY_SCHEMA_TABLES, schemaLoader.getTableCount());

        // Jira configuration and session state
        status.put(KEY_JIRA_CONFIGURED, tokenService.hasJiraToken(session));
        status.put(KEY_JIRA_BASEURL_CONFIGURED, isJiraBaseUrlConfigured());
        status.put(KEY_JIRA_WRITE_ENABLED, jiraWriteService.isJiraWriteModeEnabled(session));

        // Bitbucket configuration
        status.put(KEY_BITBUCKET_CONFIGURED, tokenService.hasBitbucketToken(session));
        status.put(KEY_BITBUCKET_BASEURL_CONFIGURED, isBitbucketBaseUrlConfigured());

        // Export data availability
        status.put(KEY_HAS_EXPORT, exportService.hasQueryResult(session));

        return status;
    }

    /**
     * Checks if the Jira base URL is configured in application properties.
     *
     * @return true if Jira base URL is set and not blank
     */
    public boolean isJiraBaseUrlConfigured() {
        String baseUrl = jiraConfig.getBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * Checks if the Bitbucket base URL is configured in application properties.
     *
     * @return true if Bitbucket base URL is set and not blank
     */
    public boolean isBitbucketBaseUrlConfigured() {
        String baseUrl = bitbucketConfig.getBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }
}

