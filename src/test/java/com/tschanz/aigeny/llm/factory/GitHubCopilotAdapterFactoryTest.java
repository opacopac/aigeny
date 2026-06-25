package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.GitHubCopilotAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GitHubCopilotAdapterFactory}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GitHubCopilotAdapterFactory")
class GitHubCopilotAdapterFactoryTest {

    private GitHubCopilotAdapterFactory factory;

    @Mock
    private AigenyProperties properties;

    @Mock
    private AigenyProperties.Llm llmProperties;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        factory = new GitHubCopilotAdapterFactory();
        lenient().when(properties.getLlm()).thenReturn(llmProperties);
    }

    @Test
    @DisplayName("should return 'github-copilot' as provider name")
    void shouldReturnGitHubCopilotAsProviderName() {
        assertThat(factory.getProviderName()).isEqualTo("github-copilot");
    }

    @Test
    @DisplayName("should support 'github-copilot' provider (case-insensitive)")
    void shouldSupportGitHubCopilotProvider() {
        assertThat(factory.supports("github-copilot")).isTrue();
        assertThat(factory.supports("GitHub-Copilot")).isTrue();
        assertThat(factory.supports("GITHUB-COPILOT")).isTrue();
    }

    @Test
    @DisplayName("should not support other providers")
    void shouldNotSupportOtherProviders() {
        assertThat(factory.supports("claude")).isFalse();
        assertThat(factory.supports("openai")).isFalse();
        assertThat(factory.supports("ollama")).isFalse();
    }

    @Test
    @DisplayName("should create GitHubCopilotAdapter instance")
    void shouldCreateGitHubCopilotAdapter() {
        // Given
        when(llmProperties.getModel()).thenReturn("gpt-4o");

        // When
        LlmClient client = factory.createAdapter(properties, githubService);

        // Then
        assertThat(client).isInstanceOf(GitHubCopilotAdapter.class);
    }

    @Test
    @DisplayName("should pass GitHubCopilotService to adapter")
    void shouldPassGitHubServiceToAdapter() {
        // Given
        when(llmProperties.getModel()).thenReturn("gpt-4o");

        // When
        LlmClient client = factory.createAdapter(properties, githubService);

        // Then
        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(GitHubCopilotAdapter.class);
    }
}




