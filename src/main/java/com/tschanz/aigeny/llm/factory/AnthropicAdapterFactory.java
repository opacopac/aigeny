package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.AnthropicAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import org.springframework.stereotype.Component;

/**
 * Factory for creating Anthropic Claude API adapters.
 */
@Component
public class AnthropicAdapterFactory implements LlmAdapterFactory {

    @Override
    public String getProviderName() {
        return "claude";
    }

    @Override
    public LlmClient createAdapter(AigenyProperties props, GitHubCopilotService githubService) {
        return new AnthropicAdapter(props);
    }
}

