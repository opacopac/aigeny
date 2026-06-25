package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;

/**
 * Factory interface for creating LLM adapters based on provider name.
 * This follows the Open/Closed Principle - new providers can be added
 * by creating new factory implementations without modifying existing code.
 */
public interface LlmAdapterFactory {

    /**
     * @return The provider name this factory supports (e.g., "claude", "github-copilot")
     */
    String getProviderName();

    /**
     * Creates an LLM adapter instance for the provider.
     *
     * @param props Configuration properties
     * @param githubService GitHub Copilot service (may be null for providers that don't need it)
     * @return Configured LLM client
     */
    LlmClient createAdapter(AigenyProperties props, GitHubCopilotService githubService);

    /**
     * Checks if this factory supports the given provider name.
     * Default implementation does case-insensitive comparison with getProviderName().
     *
     * @param providerName The provider name to check
     * @return true if this factory supports the provider
     */
    default boolean supports(String providerName) {
        return getProviderName().equalsIgnoreCase(providerName);
    }
}

