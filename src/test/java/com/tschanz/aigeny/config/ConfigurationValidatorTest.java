package com.tschanz.aigeny.config;

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

        @Test
        @DisplayName("should return true when DB is fully configured")
        void shouldReturnTrueWhenDbIsFullyConfigured() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("jdbc:oracle:thin:@localhost:1521/XE");
            db.setUsername("testuser");
            db.setPassword("testpass");

            // When
            boolean result = validator.isDbConfigured(db);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when DB config is null")
        void shouldReturnFalseWhenDbConfigIsNull() {
            // When
            boolean result = validator.isDbConfigured(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB URL is null")
        void shouldReturnFalseWhenDbUrlIsNull() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl(null);
            db.setUsername("testuser");

            // When
            boolean result = validator.isDbConfigured(db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB URL is blank")
        void shouldReturnFalseWhenDbUrlIsBlank() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("   ");
            db.setUsername("testuser");

            // When
            boolean result = validator.isDbConfigured(db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB username is null")
        void shouldReturnFalseWhenDbUsernameIsNull() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("jdbc:oracle:thin:@localhost:1521/XE");
            db.setUsername(null);

            // When
            boolean result = validator.isDbConfigured(db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when DB username is blank")
        void shouldReturnFalseWhenDbUsernameIsBlank() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("jdbc:oracle:thin:@localhost:1521/XE");
            db.setUsername("");

            // When
            boolean result = validator.isDbConfigured(db);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true even when password is missing (can use wallet)")
        void shouldReturnTrueEvenWhenPasswordIsMissing() {
            // Given
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("jdbc:oracle:thin:@localhost:1521/XE");
            db.setUsername("testuser");
            db.setPassword(null);  // Password is not required for validation

            // When
            boolean result = validator.isDbConfigured(db);

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
}

