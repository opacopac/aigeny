package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.LlmConfiguration;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.OpenAiCompatibleAdapter;
import org.springframework.stereotype.Component;

/**
 * Default/fallback factory for OpenAI-compatible LLM providers.
 * Supports: ollama, groq, openai, azure, grok, and any unknown providers.
 * This factory serves as a catch-all fallback by always returning true in supports().
 */
@Component
public class OpenAiCompatibleAdapterFactory implements LlmAdapterFactory {

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    @Override
    public LlmClient createAdapter(LlmConfiguration config, GitHubCopilotService githubService) {
        return new OpenAiCompatibleAdapter(config);
    }

    /**
     * This factory supports any provider that isn't handled by a more specific factory.
     * It always returns true to serve as a fallback.
     */
    @Override
    public boolean supports(String providerName) {
        return true; // Fallback for all providers
    }
}



