package com.tschanz.aigeny.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service responsible for resolving Docker secret files.
 * <p>
 * Supports environment variables ending in _FILE that point to secret files:
 * - AIGENY_DB_PASSWORD_FILE
 * - AIGENY_JIRA_TOKEN_FILE
 * - AIGENY_LLM_API_KEY_FILE
 * - AIGENY_BITBUCKET_TOKEN_FILE
 * <p>
 * This follows the Docker Secrets pattern where sensitive data is mounted
 * as files in containers rather than passed as environment variables.
 */
@Service
public class SecretFileResolver {

    private static final Logger log = LoggerFactory.getLogger(SecretFileResolver.class);

    /**
     * Resolves all secret files for the given AigenyProperties instance.
     * Updates the properties in place by reading from files specified in
     * *_FILE environment variables.
     *
     * @param props the properties to update with secrets from files
     */
    public void resolveSecrets(AigenyProperties props) {
        if (props == null) {
            log.warn("Cannot resolve secrets for null properties");
            return;
        }

        AigenyProperties.Db db = props.getDb();
        if (db != null) {
            db.setPassword(readSecretFile("AIGENY_DB_PASSWORD_FILE", db.getPassword()));
        }

        AigenyProperties.Jira jira = props.getJira();
        if (jira != null) {
            jira.setToken(readSecretFile("AIGENY_JIRA_TOKEN_FILE", jira.getToken()));
        }

        AigenyProperties.Llm llm = props.getLlm();
        if (llm != null) {
            llm.setApiKey(readSecretFile("AIGENY_LLM_API_KEY_FILE", llm.getApiKey()));
        }

        AigenyProperties.Bitbucket bitbucket = props.getBitbucket();
        if (bitbucket != null) {
            bitbucket.setToken(readSecretFile("AIGENY_BITBUCKET_TOKEN_FILE", bitbucket.getToken()));
        }

        log.info("Secret file resolution completed");
    }

    /**
     * Reads a secret from a file path specified in an environment variable.
     * If the environment variable is not set or the file cannot be read,
     * returns the current value unchanged.
     *
     * @param envVar       the environment variable name (e.g., "AIGENY_DB_PASSWORD_FILE")
     * @param currentValue the current/fallback value to use if file cannot be read
     * @return the secret value from the file, or currentValue if unavailable
     */
    public String readSecretFile(String envVar, String currentValue) {
        String filePath = System.getenv(envVar);
        if (filePath == null || filePath.isBlank()) {
            return currentValue;
        }

        try {
            String value = Files.readString(Path.of(filePath)).strip();
            log.info("Loaded secret from {} ({})", filePath, envVar);
            return value;
        } catch (IOException e) {
            log.warn("Could not read secret file '{}' for {}: {}", filePath, envVar, e.getMessage());
            return currentValue;
        }
    }
}

