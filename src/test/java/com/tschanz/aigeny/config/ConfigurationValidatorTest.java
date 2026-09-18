package com.tschanz.aigeny.config;
import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.database.DbMcpConfiguration;
import com.tschanz.aigeny.database.DbServerConfiguration;
import com.tschanz.aigeny.database.DbServerStage;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigurationValidator")
class ConfigurationValidatorTest {

    private ConfigurationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConfigurationValidator();
    }

    @Nested
    @DisplayName("Database Configuration Validation")
    class DatabaseConfigurationValidation {

        private AigenyProperties.Db dbWithDefaultStage(String url, String username) {
            AigenyProperties.Db db = new AigenyProperties.Db();
            AigenyProperties.Db.StageProps stage = new AigenyProperties.Db.StageProps();
            stage.setUrl(url);
            stage.setUsername(username);
            db.getStages().put(db.getDefaultStage(), stage);
            return db;
        }

        @Test
        @DisplayName("should return true when DB is fully configured")
        void shouldReturnTrueWhenDbIsFullyConfigured() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage("jdbc:oracle:thin:@localhost:1521/XE", "testuser");
            db.setPassword("testpass");

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when DB config is null")
        void shouldReturnFalseWhenDbConfigIsNull() {
            // When
            boolean result = validator.isDbConfigured(null, null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when no stage is configured at all")
        void shouldReturnFalseWhenNoStageConfigured() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB URL is null")
        void shouldReturnFalseWhenDbUrlIsNull() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage(null, "testuser");

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB URL is blank")
        void shouldReturnFalseWhenDbUrlIsBlank() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage("   ", "testuser");

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB username is null")
        void shouldReturnFalseWhenDbUsernameIsNull() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage("jdbc:oracle:thin:@localhost:1521/XE", null);

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB username is blank")
        void shouldReturnFalseWhenDbUsernameIsBlank() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage("jdbc:oracle:thin:@localhost:1521/XE", "");

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true even when password is missing (can use wallet)")
        void shouldReturnTrueEvenWhenPasswordIsMissing() {
            // Given
            AigenyProperties.Db db = dbWithDefaultStage("jdbc:oracle:thin:@localhost:1521/XE", "testuser");
            db.setPassword(null);  // Password is not required for validation

            // When
            boolean result = validator.isDbConfigured(db, db);

            // Then
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Jira Configuration Validation")
    class JiraConfigurationValidation {

        @Test
        @DisplayName("should return true when Jira is fully configured")
        void shouldReturnTrueWhenJiraIsFullyConfigured() {
            // Given
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl("https://jira.example.com");
            jira.setToken("jira-token-123");

            // When
            boolean result = validator.isJiraConfigured(jira);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when Jira config is null")
        void shouldReturnFalseWhenJiraConfigIsNull() {
            // When
            boolean result = validator.isJiraConfigured(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira base URL is null")
        void shouldReturnFalseWhenJiraBaseUrlIsNull() {
            // Given
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl(null);
            jira.setToken("token");

            // When
            boolean result = validator.isJiraConfigured(jira);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira base URL is blank")
        void shouldReturnFalseWhenJiraBaseUrlIsBlank() {
            // Given
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl("  ");
            jira.setToken("token");

            // When
            boolean result = validator.isJiraConfigured(jira);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira token is null")
        void shouldReturnFalseWhenJiraTokenIsNull() {
            // Given
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl("https://jira.example.com");
            jira.setToken(null);

            // When
            boolean result = validator.isJiraConfigured(jira);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira token is blank")
        void shouldReturnFalseWhenJiraTokenIsBlank() {
            // Given
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl("https://jira.example.com");
            jira.setToken("");

            // When
            boolean result = validator.isJiraConfigured(jira);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Bitbucket Configuration Validation")
    class BitbucketConfigurationValidation {

        @Test
        @DisplayName("should return true when Bitbucket is fully configured")
        void shouldReturnTrueWhenBitbucketIsFullyConfigured() {
            // Given
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl("https://bitbucket.example.com");
            bitbucket.setToken("bitbucket-token-123");

            // When
            boolean result = validator.isBitbucketConfigured(bitbucket);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when Bitbucket config is null")
        void shouldReturnFalseWhenBitbucketConfigIsNull() {
            // When
            boolean result = validator.isBitbucketConfigured(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket base URL is null")
        void shouldReturnFalseWhenBitbucketBaseUrlIsNull() {
            // Given
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl(null);
            bitbucket.setToken("token");

            // When
            boolean result = validator.isBitbucketConfigured(bitbucket);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket base URL is blank")
        void shouldReturnFalseWhenBitbucketBaseUrlIsBlank() {
            // Given
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl("");
            bitbucket.setToken("token");

            // When
            boolean result = validator.isBitbucketConfigured(bitbucket);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket token is null")
        void shouldReturnFalseWhenBitbucketTokenIsNull() {
            // Given
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl("https://bitbucket.example.com");
            bitbucket.setToken(null);

            // When
            boolean result = validator.isBitbucketConfigured(bitbucket);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket token is blank")
        void shouldReturnFalseWhenBitbucketTokenIsBlank() {
            // Given
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl("https://bitbucket.example.com");
            bitbucket.setToken("   ");

            // When
            boolean result = validator.isBitbucketConfigured(bitbucket);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("LLM Configuration Validation")
    class LlmConfigurationValidation {

        @Test
        @DisplayName("should return true when LLM is fully configured")
        void shouldReturnTrueWhenLlmIsFullyConfigured() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            llm.setApiKey("api-key-123");
            llm.setBaseUrl("https://api.anthropic.com");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when LLM config is null")
        void shouldReturnFalseWhenLlmConfigIsNull() {
            // When
            boolean result = validator.isLlmConfigured(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM provider is null")
        void shouldReturnFalseWhenLlmProviderIsNull() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider(null);
            llm.setApiKey("api-key");
            llm.setBaseUrl("https://api.example.com");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM provider is blank")
        void shouldReturnFalseWhenLlmProviderIsBlank() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("  ");
            llm.setApiKey("api-key");
            llm.setBaseUrl("https://api.example.com");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM API key is null")
        void shouldReturnFalseWhenLlmApiKeyIsNull() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            llm.setApiKey(null);
            llm.setBaseUrl("https://api.example.com");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM API key is blank")
        void shouldReturnFalseWhenLlmApiKeyIsBlank() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            llm.setApiKey("");
            llm.setBaseUrl("https://api.example.com");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM base URL is null")
        void shouldReturnFalseWhenLlmBaseUrlIsNull() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            llm.setApiKey("api-key");
            llm.setBaseUrl(null);

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when LLM base URL is blank")
        void shouldReturnFalseWhenLlmBaseUrlIsBlank() {
            // Given
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            llm.setApiKey("api-key");
            llm.setBaseUrl("   ");

            // When
            boolean result = validator.isLlmConfigured(llm);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("DIP – interface-based validation (not tied to AigenyProperties)")
    class InterfaceBasedValidation {

        @Test
        @DisplayName("isDbConfigured accepts arbitrary DbServerConfiguration/DbMcpConfiguration implementations")
        void isDbConfiguredAcceptsArbitraryImplementation() {
            DbServerStage stage = new DbServerStage() {
                public String getUrl()             { return "jdbc:oracle:thin:@host:1521/XE"; }
                public String getUsername()        { return "user"; }
                public String getPassword()        { return "pass"; }
                public String getSchema()          { return ""; }
                public String getEffectiveSchema() { return "user"; }
            };
            DbServerConfiguration serverConfig = new DbServerConfiguration() {
                public java.util.Map<String, ? extends DbServerStage> getStages() { return java.util.Map.of("INTE", stage); }
            };
            DbMcpConfiguration mcpConfig = new DbMcpConfiguration() {
                public String getDefaultContext()  { return "pflege"; }
                public String getDefaultStage()    { return "INTE"; }
                public String getMcpServerUrl()    { return ""; }
                public java.util.Map<String, String> getMcpServerHeaders() { return java.util.Map.of(); }
            };
            assertThat(validator.isDbConfigured(serverConfig, mcpConfig)).isTrue();
        }

        @Test
        @DisplayName("isJiraConfigured accepts arbitrary JiraConfiguration implementations")
        void isJiraConfiguredAcceptsArbitraryImplementation() {
            JiraConfiguration config = new JiraConfiguration() {
                public String getBaseUrl() { return "https://jira.example.com"; }
                public String getToken()   { return "token123"; }
            };
            assertThat(validator.isJiraConfigured(config)).isTrue();
        }

        @Test
        @DisplayName("isBitbucketConfigured accepts arbitrary BitbucketConfiguration implementations")
        void isBitbucketConfiguredAcceptsArbitraryImplementation() {
            BitbucketConfiguration config = new BitbucketConfiguration() {
                public String getBaseUrl() { return "https://bb.example.com"; }
                public String getToken()   { return "bb-token"; }
            };
            assertThat(validator.isBitbucketConfigured(config)).isTrue();
        }

        @Test
        @DisplayName("isLlmConfigured accepts arbitrary LlmConfiguration implementations")
        void isLlmConfiguredAcceptsArbitraryImplementation() {
            LlmConfiguration config = new LlmConfiguration() {
                public String getProvider() { return "claude"; }
                public String getApiKey()   { return "sk-test"; }
                public String getBaseUrl()  { return "https://api.anthropic.com/v1"; }
                public String getModel()    { return "claude-opus-4-5"; }
                public int getMaxTokens()   { return 32000; }
            };
            assertThat(validator.isLlmConfigured(config)).isTrue();
        }
    }
}

