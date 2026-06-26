package com.tschanz.aigeny.llm.factory;

import com.tschanz.aigeny.config.LlmConfiguration;
import com.tschanz.aigeny.llm.GitHubCopilotAdapter;
import com.tschanz.aigeny.llm.GitHubCopilotService;
import com.tschanz.aigeny.llm.LlmClient;
import org.springframework.stereotype.Component;

/**
 * Factory for creating GitHub Copilot LLM adapters.
 */
@Component
public class GitHubCopilotAdapterFactory implements LlmAdapterFactory {

    @Override
    public String getProviderName() {
        return "github-copilot";
    }

    @Override
    public LlmClient createAdapter(LlmConfiguration config, GitHubCopilotService githubService) {
        return new GitHubCopilotAdapter(config, githubService);
    }
}

