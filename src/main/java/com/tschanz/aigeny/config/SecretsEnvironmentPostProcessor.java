package com.tschanz.aigeny.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads Docker secrets from files referenced by *_FILE environment variables.
 *
 * Convention: if AIGENY_DB_PASSWORD_FILE=/run/secrets/db_password is set,
 * the contents of that file are loaded as the value of AIGENY_DB_PASSWORD.
 *
 * This allows sensitive values (passwords, API tokens) to be stored as
 * Docker secrets rather than plain-text environment variables.
 *
 * Registered in META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor
 */
public class SecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SecretsEnvironmentPostProcessor.class);

    /** Env vars for which we support _FILE variants */
    private static final String[] SECRET_KEYS = {
            "AIGENY_DB_PASSWORD",
            "AIGENY_JIRA_TOKEN",
            "AIGENY_LLM_API_KEY"
    };

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Map<String, Object> secrets = new HashMap<>();

        for (String key : SECRET_KEYS) {
            String fileKey   = key + "_FILE";
            String filePath  = System.getenv(fileKey);
            if (filePath == null || filePath.isBlank()) continue;

            try {
                String value = Files.readString(Path.of(filePath)).strip();
                // Map  AIGENY_DB_PASSWORD_FILE  →  aigeny.db.password  (Spring relaxed binding)
                String propKey = toPropertyKey(key);
                secrets.put(propKey, value);
                log.info("Loaded secret '{}' from file {}", propKey, filePath);
            } catch (IOException e) {
                log.warn("Could not read secret file '{}' for key '{}': {}", filePath, key, e.getMessage());
            }
        }

        if (!secrets.isEmpty()) {
            env.getPropertySources().addFirst(
                    new MapPropertySource("docker-secrets", secrets));
        }
    }

    /**
     * Maps known secret env keys to their Spring property path.
     * AIGENY_DB_PASSWORD  →  aigeny.db.password
     * AIGENY_JIRA_TOKEN   →  aigeny.jira.token
     * AIGENY_LLM_API_KEY  →  aigeny.llm.api-key
     */
    private static String toPropertyKey(String envKey) {
        return switch (envKey) {
            case "AIGENY_DB_PASSWORD"  -> "aigeny.db.password";
            case "AIGENY_JIRA_TOKEN"   -> "aigeny.jira.token";
            case "AIGENY_LLM_API_KEY"  -> "aigeny.llm.api-key";
            default -> envKey.toLowerCase().replace('_', '.');
        };
    }
}


