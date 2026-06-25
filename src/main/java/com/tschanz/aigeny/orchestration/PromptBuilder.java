package com.tschanz.aigeny.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Responsible for building system prompts from templates.
 * Single Responsibility: Prompt construction logic.
 */
@Service
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private final String systemPromptTemplate;

    /**
     * Loads the system prompt template from classpath.
     */
    public PromptBuilder() throws IOException {
        this.systemPromptTemplate = loadSystemPromptTemplate();
        log.info("PromptBuilder initialized with system prompt template");
    }

    /**
     * For testing purposes - allows injection of custom template.
     */
    PromptBuilder(String customTemplate) {
        this.systemPromptTemplate = customTemplate;
        log.debug("PromptBuilder initialized with custom template");
    }

    /**
     * Builds the system prompt for the LLM.
     * Currently returns the template as-is, but can be extended
     * for dynamic content (e.g., context injection, user preferences).
     *
     * @return the system prompt string
     */
    public String buildSystemPrompt() {
        return systemPromptTemplate;
    }

    /**
     * Gets the raw template (useful for testing/debugging).
     *
     * @return the template string
     */
    public String getTemplate() {
        return systemPromptTemplate;
    }

    private String loadSystemPromptTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("system-prompt.txt");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}

