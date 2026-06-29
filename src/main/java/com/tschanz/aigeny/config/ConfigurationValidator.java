package com.tschanz.aigeny.config;
import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.database.DbConfiguration;
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
     * A database is considered configured if both URL and username are provided.
     *
     * @param db the database configuration to validate
     * @return true if database is properly configured, false otherwise
     */
    public boolean isDbConfigured(DbConfiguration db) {
        if (db == null) {
            return false;
        }
        return db.getUrl() != null && !db.getUrl().isBlank()
            && db.getUsername() != null && !db.getUsername().isBlank();
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

