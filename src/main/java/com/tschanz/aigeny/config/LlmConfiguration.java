package com.tschanz.aigeny.config;

/**
 * Read-only view of the LLM configuration used by the AI language model adapters.
 * <p>
 * Depend on this interface instead of {@link AigenyProperties} to keep LLM
 * adapter classes decoupled from the concrete configuration holder.
 */
public interface LlmConfiguration {

    /** Provider name (e.g. ollama, claude, github-copilot, openai). */
    String getProvider();

    /** API key – "ollama" is used as a placeholder for local Ollama. */
    String getApiKey();

    /** OpenAI-compatible base URL (e.g. http://localhost:11434/v1). */
    String getBaseUrl();

    /** Model name (e.g. llama3.1:8b, claude-opus-4-5). */
    String getModel();
}
