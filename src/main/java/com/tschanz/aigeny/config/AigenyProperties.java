package com.tschanz.aigeny.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * All AIgeny configuration, bound from application.yml (prefix: aigeny).
 *
 * External override file: ~/.aigeny/aigeny.yml
 * Environment variable pattern: AIGENY_LLM_BASE_URL, AIGENY_DB_PASSWORD, etc.
 * (Spring Boot maps aigeny.llm.base-url → AIGENY_LLM_BASE_URL automatically)
 */
@Component
@ConfigurationProperties(prefix = "aigeny")
public class AigenyProperties {

    private static final Logger log = LoggerFactory.getLogger(AigenyProperties.class);

    private Llm llm = new Llm();
    private Db db = new Db();
    private Jira jira = new Jira();
    private Bitbucket bitbucket = new Bitbucket();

    /**
     * After Spring has bound all properties (YAML + env vars), resolve any
     * Docker secret files referenced by *_FILE environment variables.
     *
     * AIGENY_DB_PASSWORD_FILE  → db.password
     * AIGENY_JIRA_TOKEN_FILE   → jira.token
     * AIGENY_LLM_API_KEY_FILE  → llm.apiKey
     */
    @PostConstruct
    public void resolveSecretFiles() {
        db.password  = readSecretFile("AIGENY_DB_PASSWORD_FILE",  db.password);
        jira.token   = readSecretFile("AIGENY_JIRA_TOKEN_FILE",   jira.token);
        llm.apiKey   = readSecretFile("AIGENY_LLM_API_KEY_FILE",  llm.apiKey);
        bitbucket.token = readSecretFile("AIGENY_BITBUCKET_TOKEN_FILE", bitbucket.token);
    }

    private static String readSecretFile(String envVar, String currentValue) {
        String filePath = System.getenv(envVar);
        if (filePath == null || filePath.isBlank()) return currentValue;
        try {
            String value = Files.readString(Path.of(filePath)).strip();
            log.info("Loaded secret from {} ({})", filePath, envVar);
            return value;
        } catch (IOException e) {
            log.warn("Could not read secret file '{}' for {}: {}", filePath, envVar, e.getMessage());
            return currentValue;
        }
    }

    // ── Computed helpers ────────────────────────────────────────────────────

    public boolean isDbConfigured() {
        return db.url != null && !db.url.isBlank()
            && db.username != null && !db.username.isBlank();
    }

    public boolean isJiraConfigured() {
        return jira.baseUrl != null && !jira.baseUrl.isBlank()
            && jira.token != null && !jira.token.isBlank();
    }

    public boolean isBitbucketConfigured() {
        return bitbucket.baseUrl != null && !bitbucket.baseUrl.isBlank()
            && bitbucket.token != null && !bitbucket.token.isBlank();
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Llm getLlm()   { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public Db getDb()     { return db; }
    public void setDb(Db db) { this.db = db; }

    public Jira getJira() { return jira; }
    public void setJira(Jira jira) { this.jira = jira; }

    public Bitbucket getBitbucket() { return bitbucket; }
    public void setBitbucket(Bitbucket bitbucket) { this.bitbucket = bitbucket; }

    // ── Nested classes ──────────────────────────────────────────────────────

    public static class Llm {
        /** Provider name: ollama | groq | openai | azure */
        private String provider = "ollama";
        /** API key - use "ollama" as placeholder for local Ollama */
        private String apiKey = "ollama";
        /** OpenAI-compatible base URL */
        private String baseUrl = "http://localhost:11434/v1";
        /** Model name */
        private String model = "llama3.1:8b";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Db {
        /** JDBC URL, e.g. jdbc:oracle:thin:@hostname:1521/SERVICENAME */
        private String url = "";
        private String username = "";
        private String password = "";
        /**
         * Optional Oracle schema to set as CURRENT_SCHEMA for the session.
         * When blank the username is used as the schema (Oracle default).
         * Set this when the DB user and the data schema are different,
         * e.g. username=READONLY_USER, schema=NOVAP_INTE.
         */
        private String schema = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }

        /**
         * Returns the effective Oracle schema name.
         * Uses the explicitly configured schema if set, otherwise falls back to the username
         * (in Oracle the username equals the schema by default).
         */
        public String getEffectiveSchema() {
            return (schema != null && !schema.isBlank()) ? schema : username;
        }
    }

    public static class Jira {
        /** Jira base URL, e.g. https://flow.sbb.ch */
        private String baseUrl = "";
        /** API token (Personal Access Token) – entered per-user via the UI */
        private String token = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class Bitbucket {
        /** Bitbucket Server base URL, e.g. https://code.example.com */
        private String baseUrl = "";
        /** Personal Access Token – entered per-user via the UI */
        private String token = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}

