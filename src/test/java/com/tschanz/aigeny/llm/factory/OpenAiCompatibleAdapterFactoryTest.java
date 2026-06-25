package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.OpenAiCompatibleAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiCompatibleAdapterFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAiCompatibleAdapterFactory")
class OpenAiCompatibleAdapterFactoryTest {

    private OpenAiCompatibleAdapterFactory factory;

    @Mock
    private AigenyProperties properties;

    @Mock
    private AigenyProperties.Llm llmProperties;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        factory = new OpenAiCompatibleAdapterFactory();
        lenient().when(properties.getLlm()).thenReturn(llmProperties);
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
        LlmClient client = factory.createAdapter(properties, githubService);

        // Then
        assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
    }

    @Test
    @DisplayName("should create adapter without requiring GitHubCopilotService")
    void shouldCreateAdapterWithoutGithubService() {
        // When
        LlmClient client = factory.createAdapter(properties, null);

        // Then
        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(OpenAiCompatibleAdapter.class);
    }
}




