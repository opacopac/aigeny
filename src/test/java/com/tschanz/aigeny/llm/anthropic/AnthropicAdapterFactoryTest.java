package com.tschanz.aigeny.llm.anthropic;

import com.tschanz.aigeny.llm.LlmConfiguration;
import com.tschanz.aigeny.llm.anthropic.AnthropicAdapter;
import com.tschanz.aigeny.llm.github.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicAdapterFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnthropicAdapterFactory")
class AnthropicAdapterFactoryTest {

    private AnthropicAdapterFactory factory;

    @Mock
    private LlmConfiguration llmConfig;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        factory = new AnthropicAdapterFactory();
    }

    @Test
    @DisplayName("should return 'claude' as provider name")
    void shouldReturnClaudeAsProviderName() {
        assertThat(factory.getProviderName()).isEqualTo("claude");
    }

    @Test
    @DisplayName("should support 'claude' provider (case-insensitive)")
    void shouldSupportClaudeProvider() {
        assertThat(factory.supports("claude")).isTrue();
        assertThat(factory.supports("Claude")).isTrue();
        assertThat(factory.supports("CLAUDE")).isTrue();
    }

    @Test
    @DisplayName("should not support other providers")
    void shouldNotSupportOtherProviders() {
        assertThat(factory.supports("openai")).isFalse();
        assertThat(factory.supports("github-copilot")).isFalse();
        assertThat(factory.supports("ollama")).isFalse();
    }

    @Test
    @DisplayName("should create AnthropicAdapter instance")
    void shouldCreateAnthropicAdapter() {
        // When
        LlmClient client = factory.createAdapter(llmConfig, githubService);

        // Then
        assertThat(client).isInstanceOf(AnthropicAdapter.class);
    }

    @Test
    @DisplayName("should create adapter without requiring GitHubCopilotService")
    void shouldCreateAdapterWithoutGithubService() {
        // When
        LlmClient client = factory.createAdapter(llmConfig, null);

        // Then
        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(AnthropicAdapter.class);
    }
}
