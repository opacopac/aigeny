package com.tschanz.aigeny.config;
import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.database.DbConfiguration;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All AIgeny configuration, bound from application.yml (prefix: aigeny).
 * <p>
 * This class is a pure configuration holder that only contains property bindings.
 * Business logic has been extracted to separate services:
 * <ul>
 *   <li>{@link SecretFileResolver} - Handles Docker secret file resolution</li>
 *   <li>{@link ConfigurationValidator} - Validates configuration completeness</li>
 * </ul>
 * <p>
 * External override file: ~/.aigeny/aigeny.yml
 * Environment variable pattern: AIGENY_LLM_BASE_URL, AIGENY_DB_PASSWORD, etc.
 * (Spring Boot maps aigeny.llm.base-url → AIGENY_LLM_BASE_URL automatically)
 */
@Component
@ConfigurationProperties(prefix = "aigeny")
public class AigenyProperties {

    private final SecretFileResolver secretFileResolver;

    private Llm llm = new Llm();
    private Db db = new Db();
    private Jira jira = new Jira();
    private Bitbucket bitbucket = new Bitbucket();

    public AigenyProperties(SecretFileResolver secretFileResolver) {
        this.secretFileResolver = secretFileResolver;
    }

    /**
     * After Spring has bound all properties (YAML + env vars), resolve any
     * Docker secret files referenced by *_FILE environment variables.
     * <p>
     * Delegates to {@link SecretFileResolver} for actual file reading.
     */
    @PostConstruct
    public void resolveSecretFiles() {
        secretFileResolver.resolveSecrets(this);
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

    public static class Llm implements LlmConfiguration {
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

    public static class Db implements DbConfiguration {
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

    public static class Jira implements JiraConfiguration {
        /** Jira base URL, e.g. https://flow.sbb.ch */
        private String baseUrl = "";
        /** API token (Personal Access Token) – entered per-user via the UI */
        private String token = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class Bitbucket implements BitbucketConfiguration {
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

