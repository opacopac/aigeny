package com.tschanz.aigeny.llm.openai;

import com.tschanz.aigeny.config.LlmConfiguration;
import com.tschanz.aigeny.llm.github.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.openai.OpenAiCompatibleAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiCompatibleAdapterFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAiCompatibleAdapterFactory")
class OpenAiCompatibleAdapterFactoryTest {

    private OpenAiCompatibleAdapterFactory factory;

    @Mock
    private LlmConfiguration llmConfig;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        factory = new OpenAiCompatibleAdapterFactory();
    }

    @Test
    @DisplayName("should return 'openai-compatible' as provider name")
    void shouldReturnOpenAiCompatibleAsProviderName() {
        assertThat(factory.getProviderName()).isEqualTo("openai-compatible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "ollama", "groq", "azure", "grok", "unknown-provider"})
    @DisplayName("should support all provider names (fallback)")
    void shouldSupportAllProviders(String providerName) {
        assertThat(factory.supports(providerName)).isTrue();
    }

    @Test
    @DisplayName("should support even specific providers like claude (as fallback)")
    void shouldSupportEvenSpecificProviders() {
        // This factory should support everything as a fallback
        assertThat(factory.supports("claude")).isTrue();
        assertThat(factory.supports("github-copilot")).isTrue();
    }

    @Test
    @DisplayName("should create OpenAiCompatibleAdapter instance")
    void shouldCreateOpenAiCompatibleAdapter() {
        // When
        LlmClient client = factory.createAdapter(llmConfig, githubService);

        // Then
        assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
    }

    @Test
    @DisplayName("should create adapter without requiring GitHubCopilotService")
    void shouldCreateAdapterWithoutGithubService() {
        // When
        LlmClient client = factory.createAdapter(llmConfig, null);

        // Then
        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
    }
}
