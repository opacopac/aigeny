package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.LlmConfiguration;
import com.tschanz.aigeny.llm.GitHubCopilotAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GitHubCopilotAdapterFactory}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GitHubCopilotAdapterFactory")
class GitHubCopilotAdapterFactoryTest {

    private GitHubCopilotAdapterFactory factory;

    @Mock
    private LlmConfiguration llmConfig;

    @Mock
    private GitHubCopilotService githubService;

    @BeforeEach
    void setUp() {
        factory = new GitHubCopilotAdapterFactory();
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
        // When
        LlmClient client = factory.createAdapter(llmConfig, githubService);

        // Then
        assertThat(client).isInstanceOf(GitHubCopilotAdapter.class);
    }

    @Test
    @DisplayName("should pass GitHubCopilotService to adapter")
    void shouldPassGitHubServiceToAdapter() {
        // When
        LlmClient client = factory.createAdapter(llmConfig, githubService);

        // Then
        assertThat(client).isNotNull();
        assertThat(client).isInstanceOf(GitHubCopilotAdapter.class);
    }
}
