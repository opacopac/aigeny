package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.db.SchemaLoader;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatusAggregatorService")
class StatusAggregatorServiceTest {

    private AigenyProperties props;
    private AigenyProperties.Llm llmConfig;
    private AigenyProperties.Db dbConfig;
    private AigenyProperties.Jira jiraConfig;
    private AigenyProperties.Bitbucket bitbucketConfig;

    @Mock
    private ConfigurationValidator configValidator;

    @Mock
    private TokenService tokenService;

    @Mock
    private ChatSessionService sessionService;

    @Mock
    private SchemaLoader schemaLoader;

    @Mock
    private HttpSession session;

    private StatusAggregatorService statusAggregator;

    @BeforeEach
    void setUp() {
        // Use real configuration objects (no need to spy anymore)
        props = mock(AigenyProperties.class);
        llmConfig = new AigenyProperties.Llm();
        dbConfig = new AigenyProperties.Db();
        jiraConfig = new AigenyProperties.Jira();
        bitbucketConfig = new AigenyProperties.Bitbucket();

        lenient().when(props.getLlm()).thenReturn(llmConfig);
        lenient().when(props.getDb()).thenReturn(dbConfig);
        lenient().when(props.getJira()).thenReturn(jiraConfig);
        lenient().when(props.getBitbucket()).thenReturn(bitbucketConfig);

        statusAggregator = new StatusAggregatorService(
            props,
            configValidator,
            tokenService,
            sessionService,
            schemaLoader
        );
    }

    @Nested
    @DisplayName("Status Aggregation")
    class StatusAggregation {

        @Test
        @DisplayName("should aggregate complete status with all services configured")
        void shouldAggregateCompleteStatusWhenAllConfigured() {
            // Given
            llmConfig.setProvider("claude");
            llmConfig.setModel("claude-3-sonnet");
            dbConfig.setUsername("dbuser");
            jiraConfig.setBaseUrl("https://jira.example.com");
            bitbucketConfig.setBaseUrl("https://bitbucket.example.com");

            when(tokenService.hasJiraToken(session)).thenReturn(true);
            when(tokenService.hasBitbucketToken(session)).thenReturn(true);
            when(sessionService.isJiraWriteModeEnabled(session)).thenReturn(true);
            when(sessionService.hasQueryResult(session)).thenReturn(true);
            when(schemaLoader.getTableCount()).thenReturn(42);
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(true);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status).isNotNull();
            assertThat(status.get("llmProvider")).isEqualTo("claude");
            assertThat(status.get("llmModel")).isEqualTo("claude-3-sonnet");
            assertThat(status.get("dbConfigured")).isEqualTo(true);
            assertThat(status.get("dbUsername")).isEqualTo("dbuser");
            assertThat(status.get("schemaTables")).isEqualTo(42);
            assertThat(status.get("jiraConfigured")).isEqualTo(true);
            assertThat(status.get("jiraBaseUrlConfigured")).isEqualTo(true);
            assertThat(status.get("jiraWriteEnabled")).isEqualTo(true);
            assertThat(status.get("bitbucketConfigured")).isEqualTo(true);
            assertThat(status.get("bitbucketBaseUrlConfigured")).isEqualTo(true);
            assertThat(status.get("hasExport")).isEqualTo(true);
        }

        @Test
        @DisplayName("should aggregate status with minimal configuration")
        void shouldAggregateStatusWithMinimalConfiguration() {
            // Given
            llmConfig.setProvider("openai");
            llmConfig.setModel("gpt-4");
            dbConfig.setUsername(null);
            jiraConfig.setBaseUrl(null);
            bitbucketConfig.setBaseUrl(null);

            when(tokenService.hasJiraToken(session)).thenReturn(false);
            when(tokenService.hasBitbucketToken(session)).thenReturn(false);
            when(sessionService.isJiraWriteModeEnabled(session)).thenReturn(false);
            when(sessionService.hasQueryResult(session)).thenReturn(false);
            when(schemaLoader.getTableCount()).thenReturn(0);
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(false);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status).isNotNull();
            assertThat(status.get("llmProvider")).isEqualTo("openai");
            assertThat(status.get("llmModel")).isEqualTo("gpt-4");
            assertThat(status.get("dbConfigured")).isEqualTo(false);
            assertThat(status.get("dbUsername")).isNull();
            assertThat(status.get("schemaTables")).isEqualTo(0);
            assertThat(status.get("jiraConfigured")).isEqualTo(false);
            assertThat(status.get("jiraBaseUrlConfigured")).isEqualTo(false);
            assertThat(status.get("jiraWriteEnabled")).isEqualTo(false);
            assertThat(status.get("bitbucketConfigured")).isEqualTo(false);
            assertThat(status.get("bitbucketBaseUrlConfigured")).isEqualTo(false);
            assertThat(status.get("hasExport")).isEqualTo(false);
        }

        @Test
        @DisplayName("should call all dependencies when aggregating status")
        void shouldCallAllDependenciesWhenAggregating() {
            // Given
            llmConfig.setProvider("claude");
            llmConfig.setModel("claude-3");

            // When
            statusAggregator.aggregateStatus(session);

            // Then
            verify(tokenService).hasJiraToken(session);
            verify(tokenService).hasBitbucketToken(session);
            verify(sessionService).isJiraWriteModeEnabled(session);
            verify(sessionService).hasQueryResult(session);
            verify(schemaLoader).getTableCount();
            verify(configValidator, atLeastOnce()).isDbConfigured(dbConfig);
        }

        @Test
        @DisplayName("should include all expected status keys")
        void shouldIncludeAllExpectedStatusKeys() {
            // Given
            llmConfig.setProvider("test-provider");
            llmConfig.setModel("test-model");

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status).containsKeys(
                "llmProvider",
                "llmModel",
                "dbConfigured",
                "dbUsername",
                "schemaTables",
                "jiraConfigured",
                "jiraBaseUrlConfigured",
                "jiraWriteEnabled",
                "bitbucketConfigured",
                "bitbucketBaseUrlConfigured",
                "hasExport"
            );
        }
    }

    @Nested
    @DisplayName("Jira Base URL Configuration")
    class JiraBaseUrlConfiguration {

        @Test
        @DisplayName("should return true when Jira base URL is set")
        void shouldReturnTrueWhenJiraBaseUrlIsSet() {
            // Given
            jiraConfig.setBaseUrl("https://jira.example.com");

            // When
            boolean result = statusAggregator.isJiraBaseUrlConfigured();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when Jira base URL is null")
        void shouldReturnFalseWhenJiraBaseUrlIsNull() {
            // Given
            jiraConfig.setBaseUrl(null);

            // When
            boolean result = statusAggregator.isJiraBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira base URL is blank")
        void shouldReturnFalseWhenJiraBaseUrlIsBlank() {
            // Given
            jiraConfig.setBaseUrl("   ");

            // When
            boolean result = statusAggregator.isJiraBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Jira base URL is empty string")
        void shouldReturnFalseWhenJiraBaseUrlIsEmpty() {
            // Given
            jiraConfig.setBaseUrl("");

            // When
            boolean result = statusAggregator.isJiraBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Bitbucket Base URL Configuration")
    class BitbucketBaseUrlConfiguration {

        @Test
        @DisplayName("should return true when Bitbucket base URL is set")
        void shouldReturnTrueWhenBitbucketBaseUrlIsSet() {
            // Given
            bitbucketConfig.setBaseUrl("https://bitbucket.example.com");

            // When
            boolean result = statusAggregator.isBitbucketBaseUrlConfigured();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when Bitbucket base URL is null")
        void shouldReturnFalseWhenBitbucketBaseUrlIsNull() {
            // Given
            bitbucketConfig.setBaseUrl(null);

            // When
            boolean result = statusAggregator.isBitbucketBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket base URL is blank")
        void shouldReturnFalseWhenBitbucketBaseUrlIsBlank() {
            // Given
            bitbucketConfig.setBaseUrl("  ");

            // When
            boolean result = statusAggregator.isBitbucketBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when Bitbucket base URL is empty string")
        void shouldReturnFalseWhenBitbucketBaseUrlIsEmpty() {
            // Given
            bitbucketConfig.setBaseUrl("");

            // When
            boolean result = statusAggregator.isBitbucketBaseUrlConfigured();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("LLM Configuration Status")
    class LlmConfigurationStatus {

        @Test
        @DisplayName("should aggregate LLM provider from configuration")
        void shouldAggregateLlmProviderFromConfiguration() {
            // Given
            llmConfig.setProvider("github-copilot");
            llmConfig.setModel("some-model");

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("llmProvider")).isEqualTo("github-copilot");
        }

        @Test
        @DisplayName("should aggregate LLM model from configuration")
        void shouldAggregateLlmModelFromConfiguration() {
            // Given
            llmConfig.setProvider("openai");
            llmConfig.setModel("gpt-4-turbo");

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("llmModel")).isEqualTo("gpt-4-turbo");
        }
    }

    @Nested
    @DisplayName("Database Configuration Status")
    class DatabaseConfigurationStatus {

        @Test
        @DisplayName("should aggregate database username from configuration")
        void shouldAggregateDatabaseUsernameFromConfiguration() {
            // Given
            dbConfig.setUsername("mydbuser");
            llmConfig.setProvider("test");
            llmConfig.setModel("test");

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("dbUsername")).isEqualTo("mydbuser");
        }

        @Test
        @DisplayName("should aggregate schema table count from loader")
        void shouldAggregateSchemaTableCountFromLoader() {
            // Given
            llmConfig.setProvider("test");
            llmConfig.setModel("test");
            when(schemaLoader.getTableCount()).thenReturn(99);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("schemaTables")).isEqualTo(99);
        }
    }

    @Nested
    @DisplayName("Session-Specific Status")
    class SessionSpecificStatus {

        @Test
        @DisplayName("should aggregate Jira write mode from session")
        void shouldAggregateJiraWriteModeFromSession() {
            // Given
            llmConfig.setProvider("test");
            llmConfig.setModel("test");
            when(sessionService.isJiraWriteModeEnabled(session)).thenReturn(true);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("jiraWriteEnabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("should aggregate export availability from session")
        void shouldAggregateExportAvailabilityFromSession() {
            // Given
            llmConfig.setProvider("test");
            llmConfig.setModel("test");
            when(sessionService.hasQueryResult(session)).thenReturn(true);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("hasExport")).isEqualTo(true);
        }

        @Test
        @DisplayName("should aggregate Jira token availability from token service")
        void shouldAggregateJiraTokenAvailabilityFromTokenService() {
            // Given
            llmConfig.setProvider("test");
            llmConfig.setModel("test");
            when(tokenService.hasJiraToken(session)).thenReturn(true);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("jiraConfigured")).isEqualTo(true);
        }

        @Test
        @DisplayName("should aggregate Bitbucket token availability from token service")
        void shouldAggregateBitbucketTokenAvailabilityFromTokenService() {
            // Given
            llmConfig.setProvider("test");
            llmConfig.setModel("test");
            when(tokenService.hasBitbucketToken(session)).thenReturn(true);

            // When
            Map<String, Object> status = statusAggregator.aggregateStatus(session);

            // Then
            assertThat(status.get("bitbucketConfigured")).isEqualTo(true);
        }
    }
}




