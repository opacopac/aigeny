package com.tschanz.aigeny.config;

import com.tschanz.aigeny.llm.AnthropicAdapter;
import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.OpenAiCompatibleAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the correct LlmClient bean based on aigeny.llm.provider.
 *
 * OpenAI-compatible (same adapter):
 *   ollama   → local Ollama server
 *   groq     → Groq cloud (free tier, rate-limited)
 *   openai   → OpenAI API
 *   azure    → Azure OpenAI
 *   grok     → xAI Grok API (OpenAI-compatible)
 *
 * Native API (dedicated adapter):
 *   claude   → Anthropic Claude API
 */
@Configuration
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    public LlmClient llmClient(AigenyProperties props) {
        String provider = props.getLlm().getProvider();
        log.info("Initialising LLM adapter for provider: {} / model: {}",
                provider, props.getLlm().getModel());

        return switch (provider.toLowerCase()) {
            case "claude" -> new AnthropicAdapter(props);
            default       -> new OpenAiCompatibleAdapter(props);
            // ollama, groq, openai, azure, grok all use OpenAI-compatible format
        };
    }
}

