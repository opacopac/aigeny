package com.tschanz.aigeny.config;
import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.database.DbMcpConfiguration;
import com.tschanz.aigeny.database.DbServerConfiguration;
import com.tschanz.aigeny.database.DbServerStage;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;

import org.springframework.stereotype.Service;

/**
 * Service responsible for validating configuration settings.
 * <p>
 * Provides methods to check if different parts of the AIgeny system
 * are properly configured and ready to use.
 */
@Service
public class ConfigurationValidator {

    /**
     * Checks if database configuration is complete and valid.
     * A database is considered configured if the default stage (see
     * {@link DbMcpConfiguration#getDefaultStage()}) has both a URL and a username configured.
     *
     * @param dbServer the per-stage database connection details to validate
     * @param dbMcp    the MCP context/stage defaults (selects which stage of {@code dbServer} to check)
     * @return true if database is properly configured, false otherwise
     */
    public boolean isDbConfigured(DbServerConfiguration dbServer, DbMcpConfiguration dbMcp) {
        if (dbServer == null || dbMcp == null) {
            return false;
        }
        DbServerStage stage = dbServer.getStage(dbMcp.getDefaultStage());
        if (stage == null) {
            return false;
        }
        return stage.getUrl() != null && !stage.getUrl().isBlank()
            && stage.getUsername() != null && !stage.getUsername().isBlank();
    }

    /**
     * Checks if Jira configuration is complete and valid.
     * Jira is considered configured if both base URL and token are provided.
     *
     * @param jira the Jira configuration to validate
     * @return true if Jira is properly configured, false otherwise
     */
    public boolean isJiraConfigured(JiraConfiguration jira) {
        if (jira == null) {
            return false;
        }
        return jira.getBaseUrl() != null && !jira.getBaseUrl().isBlank()
            && jira.getToken() != null && !jira.getToken().isBlank();
    }

    /**
     * Checks if Bitbucket configuration is complete and valid.
     * Bitbucket is considered configured if both base URL and token are provided.
     *
     * @param bitbucket the Bitbucket configuration to validate
     * @return true if Bitbucket is properly configured, false otherwise
     */
    public boolean isBitbucketConfigured(BitbucketConfiguration bitbucket) {
        if (bitbucket == null) {
            return false;
        }
        return bitbucket.getBaseUrl() != null && !bitbucket.getBaseUrl().isBlank()
            && bitbucket.getToken() != null && !bitbucket.getToken().isBlank();
    }

    /**
     * Checks if LLM configuration is complete and valid.
     * LLM is considered configured if provider, API key, and base URL are provided.
     *
     * @param llm the LLM configuration to validate
     * @return true if LLM is properly configured, false otherwise
     */
    public boolean isLlmConfigured(LlmConfiguration llm) {
        if (llm == null) {
            return false;
        }
        return llm.getProvider() != null && !llm.getProvider().isBlank()
            && llm.getApiKey() != null && !llm.getApiKey().isBlank()
            && llm.getBaseUrl() != null && !llm.getBaseUrl().isBlank();
    }
}

