package com.tschanz.aigeny.config;

import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.factory.LlmAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Creates the correct LlmClient bean based on aigeny.llm.provider.
 * Uses the Strategy Pattern with factory registry to allow adding new
 * providers without modifying this configuration class (Open/Closed Principle).
 *
 * Supported providers (via factories):
 *   claude         → Anthropic Claude API (AnthropicAdapterFactory)
 *   github-copilot → GitHub Copilot LLM (GitHubCopilotAdapterFactory)
 *   ollama, groq, openai, azure, grok, etc. → OpenAI-compatible (OpenAiCompatibleAdapterFactory)
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(AigenyProperties props,
                               GitHubCopilotService github,
                               List<LlmAdapterFactory> factories) {
        String provider = props.getLlm().getProvider();
        log.info("Initialising LLM adapter for provider: {} / model: {}",
                provider, props.getLlm().getModel());

        // Find the first factory that supports this provider
        return factories.stream()
                .filter(factory -> factory.supports(provider))
                .findFirst()
                .map(factory -> {
                    log.debug("Using factory: {} for provider: {}",
                            factory.getClass().getSimpleName(), provider);
                    return factory.createAdapter(props, github);
                })
                .orElseThrow(() -> new IllegalStateException(
                        "No LLM adapter factory found for provider: " + provider +
                        ". This should not happen as OpenAiCompatibleAdapterFactory serves as fallback."));
    }
}

