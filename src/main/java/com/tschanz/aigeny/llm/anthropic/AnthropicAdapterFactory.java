package com.tschanz.aigeny.llm.anthropic;
import com.tschanz.aigeny.llm.LlmAdapterFactory;

import com.tschanz.aigeny.llm.LlmConfiguration;
import com.tschanz.aigeny.llm.anthropic.AnthropicAdapter;
import com.tschanz.aigeny.llm.github.GitHubCopilotService;
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
    public LlmClient createAdapter(LlmConfiguration config, GitHubCopilotService githubService) {
        return new AnthropicAdapter(config);
    }
}

