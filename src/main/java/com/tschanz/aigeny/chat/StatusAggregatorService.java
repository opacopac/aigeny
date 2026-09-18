package com.tschanz.aigeny.chat;
import com.tschanz.aigeny.llm.github.TokenService;
import com.tschanz.aigeny.jira.SessionJiraWriteService;
import com.tschanz.aigeny.export.SessionExportService;

import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DataContextSelectionService;
import com.tschanz.aigeny.database.DbMcpConfiguration;
import com.tschanz.aigeny.database.DbServerConfiguration;
import com.tschanz.aigeny.database.DbServerStage;
import com.tschanz.aigeny.database.mcp_client.OracleMcpConnection;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Aggregates system status information from various sources including
 * LLM configuration, MCP DB connection, Jira, and Bitbucket.
 */
@Service
public class StatusAggregatorService {

    // Status map keys
    private static final String KEY_LLM_PROVIDER                 = "llmProvider";
    private static final String KEY_LLM_MODEL                    = "llmModel";
    private static final String KEY_DB_CONFIGURED                = "dbConfigured";
    private static final String KEY_DB_USERNAME                  = "dbUsername";
    private static final String KEY_DB_STAGE                     = "dbStage";
    private static final String KEY_DB_MCP_CONNECTED             = "dbMcpConnected";
    private static final String KEY_DB_MCP_LIST_TABLES_AVAILABLE = "dbMcpListTablesAvailable";
    private static final String KEY_DB_MCP_TABLE_COUNT           = "dbMcpTableCount";
    private static final String KEY_DB_MCP_ERROR                 = "dbMcpError";
    private static final String KEY_JIRA_CONFIGURED              = "jiraConfigured";
    private static final String KEY_JIRA_BASEURL_CONFIGURED      = "jiraBaseUrlConfigured";
    private static final String KEY_JIRA_WRITE_ENABLED           = "jiraWriteEnabled";
    private static final String KEY_BITBUCKET_CONFIGURED         = "bitbucketConfigured";
    private static final String KEY_BITBUCKET_BASEURL_CONFIGURED = "bitbucketBaseUrlConfigured";
    private static final String KEY_HAS_EXPORT                   = "hasExport";

    private final LlmConfiguration llmConfig;
    private final DbServerConfiguration dbServerConfig;
    private final DbMcpConfiguration dbMcpConfig;
    private final JiraConfiguration jiraConfig;
    private final BitbucketConfiguration bitbucketConfig;
    private final ConfigurationValidator configValidator;
    private final TokenService tokenService;
    private final SessionJiraWriteService jiraWriteService;
    private final SessionExportService exportService;
    private final OracleMcpConnection dbMcpConnection;
    private final DataContextSelectionService dataContextSelectionService;

    public StatusAggregatorService(LlmConfiguration llmConfig,
                                   @Qualifier("dbServerConfiguration") DbServerConfiguration dbServerConfig,
                                   @Qualifier("dbMcpConfiguration") DbMcpConfiguration dbMcpConfig,
                                   JiraConfiguration jiraConfig,
                                   BitbucketConfiguration bitbucketConfig,
                                   ConfigurationValidator configValidator,
                                   TokenService tokenService,
                                   SessionJiraWriteService jiraWriteService,
                                   SessionExportService exportService,
                                   OracleMcpConnection dbMcpConnection,
                                   DataContextSelectionService dataContextSelectionService) {
        this.llmConfig = llmConfig;
        this.dbServerConfig = dbServerConfig;
        this.dbMcpConfig = dbMcpConfig;
        this.jiraConfig = jiraConfig;
        this.bitbucketConfig = bitbucketConfig;
        this.configValidator = configValidator;
        this.tokenService = tokenService;
        this.jiraWriteService = jiraWriteService;
        this.exportService = exportService;
        this.dbMcpConnection = dbMcpConnection;
        this.dataContextSelectionService = dataContextSelectionService;
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
        status.put(KEY_DB_CONFIGURED, configValidator.isDbConfigured(dbServerConfig, dbMcpConfig));
        String selectedStage = dataContextSelectionService.getSelectedStage(session);
        status.put(KEY_DB_USERNAME, currentDbUsername(selectedStage));
        status.put(KEY_DB_STAGE, selectedStage);

        // DB MCP server status - checked live via the "list_tables" MCP tool call.
        // Named "dbMcp*" (not just "mcp*") since later on there will also be MCP
        // servers for Jira and Bitbucket - this one is specifically the DB server.
        OracleMcpConnection.McpListTablesStatus dbMcpStatus = dbMcpConnection.checkListTables(
                dataContextSelectionService.getSelectedContext(session), selectedStage);
        status.put(KEY_DB_MCP_CONNECTED, dbMcpStatus.connected());
        status.put(KEY_DB_MCP_LIST_TABLES_AVAILABLE, dbMcpStatus.available());
        status.put(KEY_DB_MCP_TABLE_COUNT, dbMcpStatus.tableCount());
        status.put(KEY_DB_MCP_ERROR, dbMcpStatus.error());

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
     * Returns the username of the given DB stage (typically the currently selected stage -
     * see {@link DataContextSelectionService#getSelectedStage(HttpSession)}), or {@code null}
     * if that stage isn't configured.
     */
    private String currentDbUsername(String stage) {
        DbServerStage dbStage = dbServerConfig.getStage(stage);
        return dbStage != null ? dbStage.getUsername() : null;
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

