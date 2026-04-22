package com.tschanz.aigeny.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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

    private Llm llm = new Llm();
    private Db db = new Db();
    private Jira jira = new Jira();

    // ── Computed helpers ────────────────────────────────────────────────────

    public boolean isDbConfigured() {
        return db.url != null && !db.url.isBlank()
            && db.username != null && !db.username.isBlank();
    }

    public boolean isJiraConfigured() {
        return jira.baseUrl != null && !jira.baseUrl.isBlank()
            && jira.token != null && !jira.token.isBlank();
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public Llm getLlm()   { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public Db getDb()     { return db; }
    public void setDb(Db db) { this.db = db; }

    public Jira getJira() { return jira; }
    public void setJira(Jira jira) { this.jira = jira; }

    // ── Nested classes ──────────────────────────────────────────────────────

    public static class Llm {
        /** Provider name: ollama | groq | openai | azure */
        private String provider = "ollama";
        /** API key — use "ollama" as placeholder for local Ollama */
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

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Jira {
        /** Jira base URL, e.g. https://flow.sbb.ch */
        private String baseUrl = "";
        private String username = "";
        /** API token or password */
        private String token = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}

