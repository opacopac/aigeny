package com.tschanz.aigeny.config;

import com.tschanz.aigeny.config.LlmConfiguration;
import com.tschanz.aigeny.llm.AnthropicAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.OpenAiCompatibleAdapter;
import com.tschanz.aigeny.llm.factory.AnthropicAdapterFactory;
import com.tschanz.aigeny.llm.factory.GitHubCopilotAdapterFactory;
import com.tschanz.aigeny.llm.factory.LlmAdapterFactory;
import com.tschanz.aigeny.llm.factory.OpenAiCompatibleAdapterFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LlmConfig}.
 * Tests the factory pattern implementation that allows adding new LLM providers
 * without modifying the configuration class (Open/Closed Principle).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LlmConfig")
class LlmConfigTest {

    private LlmConfig config;

    @Mock
    private AigenyProperties properties;

    @Mock
    private AigenyProperties.Llm llmProperties;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        config = new LlmConfig();
        lenient().when(properties.getLlm()).thenReturn(llmProperties);
    }

    @Nested
    @DisplayName("Claude Provider")
    class ClaudeProvider {

        @Test
        @DisplayName("should create AnthropicAdapter for 'claude' provider")
        void shouldCreateAnthropicAdapter() {
            // Given
            when(llmProperties.getProvider()).thenReturn("claude");
            lenient().when(llmProperties.getModel()).thenReturn("claude-sonnet-4-6");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new GitHubCopilotAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(AnthropicAdapter.class);
        }

        @Test
        @DisplayName("should handle case-insensitive provider name")
        void shouldHandleCaseInsensitiveProviderName() {
            // Given
            when(llmProperties.getProvider()).thenReturn("CLAUDE");
            lenient().when(llmProperties.getModel()).thenReturn("claude-sonnet-4-6");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(AnthropicAdapter.class);
        }
    }

    @Nested
    @DisplayName("GitHub Copilot Provider")
    class GitHubCopilotProvider {

        @Test
        @DisplayName("should create GitHubCopilotAdapter for 'github-copilot' provider")
        void shouldCreateGitHubCopilotAdapter() {
            // Given
            when(llmProperties.getProvider()).thenReturn("github-copilot");
            when(llmProperties.getModel()).thenReturn("gpt-4o");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new GitHubCopilotAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(GitHubCopilotAdapter.class);
        }
    }

    @Nested
    @DisplayName("OpenAI-Compatible Providers")
    class OpenAiCompatibleProviders {

        @Test
        @DisplayName("should create OpenAiCompatibleAdapter for 'openai' provider")
        void shouldCreateOpenAiCompatibleAdapterForOpenAi() {
            // Given
            when(llmProperties.getProvider()).thenReturn("openai");
            lenient().when(llmProperties.getModel()).thenReturn("gpt-4");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new GitHubCopilotAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
        }

        @Test
        @DisplayName("should create OpenAiCompatibleAdapter for 'ollama' provider")
        void shouldCreateOpenAiCompatibleAdapterForOllama() {
            // Given
            when(llmProperties.getProvider()).thenReturn("ollama");
            lenient().when(llmProperties.getModel()).thenReturn("llama3.2");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
        }

        @Test
        @DisplayName("should create OpenAiCompatibleAdapter for 'groq' provider")
        void shouldCreateOpenAiCompatibleAdapterForGroq() {
            // Given
            when(llmProperties.getProvider()).thenReturn("groq");
            lenient().when(llmProperties.getModel()).thenReturn("llama-3.3-70b-versatile");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
        }

        @Test
        @DisplayName("should create OpenAiCompatibleAdapter for unknown provider (fallback)")
        void shouldFallbackToOpenAiCompatibleAdapter() {
            // Given
            when(llmProperties.getProvider()).thenReturn("some-new-provider");
            lenient().when(llmProperties.getModel()).thenReturn("some-model");

            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then
            assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
        }
    }

    @Nested
    @DisplayName("Factory Registry")
    class FactoryRegistry {

        @Test
        @DisplayName("should select first matching factory when multiple factories support same provider")
        void shouldSelectFirstMatchingFactory() {
            // Given
            when(llmProperties.getProvider()).thenReturn("test-provider");
            when(llmProperties.getModel()).thenReturn("test-model");

            // Both factories will support this since OpenAiCompatible supports everything
            List<LlmAdapterFactory> factories = List.of(
                    new OpenAiCompatibleAdapterFactory(),
                    new AnthropicAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then - Should use first matching factory (OpenAiCompatible)
            assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
        }

        @Test
        @DisplayName("should throw exception when no factories are provided")
        void shouldThrowExceptionWhenNoFactories() {
            // Given
            when(llmProperties.getProvider()).thenReturn("claude");
            when(llmProperties.getModel()).thenReturn("claude-sonnet-4-6");

            List<LlmAdapterFactory> factories = List.of();

            // When/Then
            assertThatThrownBy(() -> config.llmClient(properties, githubService, factories))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No LLM adapter factory found for provider: claude");
        }

        @Test
        @DisplayName("should throw exception when only specific factories exist and none match")
        void shouldThrowExceptionWhenNoFactoryMatches() {
            // Given
            when(llmProperties.getProvider()).thenReturn("some-provider");
            when(llmProperties.getModel()).thenReturn("some-model");

            // Only specific factories, no fallback
            List<LlmAdapterFactory> factories = List.of(
                    new AnthropicAdapterFactory(),
                    new GitHubCopilotAdapterFactory()
            );

            // When/Then
            assertThatThrownBy(() -> config.llmClient(properties, githubService, factories))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No LLM adapter factory found for provider: some-provider");
        }
    }

    @Nested
    @DisplayName("Open/Closed Principle Validation")
    class OpenClosedPrincipleValidation {

        @Test
        @DisplayName("should support adding new provider via new factory without code changes")
        void shouldSupportNewProviderViaNewFactory() {
            // This test demonstrates the OCP: we can add a new provider
            // by creating a new factory, without modifying LlmConfig

            // Given - A new custom factory for a hypothetical provider
            LlmAdapterFactory customFactory = new LlmAdapterFactory() {
                @Override
                public String getProviderName() {
                    return "custom-provider";
                }

                @Override
                public LlmClient createAdapter(LlmConfiguration config, GitHubCopilotService githubService) {
                    return new OpenAiCompatibleAdapter(config);
                }
            };

            when(llmProperties.getProvider()).thenReturn("custom-provider");
            when(llmProperties.getModel()).thenReturn("custom-model");

            List<LlmAdapterFactory> factories = List.of(
                    customFactory,
                    new AnthropicAdapterFactory(),
                    new OpenAiCompatibleAdapterFactory()
            );

            // When
            LlmClient client = config.llmClient(properties, githubService, factories);

            // Then - Successfully creates adapter using the custom factory
            assertThat(client).isNotNull();
        }
    }
}









