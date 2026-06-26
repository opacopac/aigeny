package com.tschanz.aigeny.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the {@link AigenyProperties} sub-objects as dedicated Spring beans so that
 * services can depend on narrow configuration interfaces instead of the concrete
 * {@link AigenyProperties} class (Dependency Inversion Principle).
 *
 * <ul>
 *   <li>{@link LlmConfiguration}       – consumed by LLM adapters and factories</li>
 *   <li>{@link DbConfiguration}        – consumed by DB tools and schema loader</li>
 *   <li>{@link JiraConfiguration}      – consumed by Jira tools and token service</li>
 *   <li>{@link BitbucketConfiguration} – consumed by Bitbucket tools and token service</li>
 * </ul>
 *
 * {@link AigenyProperties} remains the authoritative Spring properties holder;
 * these beans are thin delegates that return the already-initialised nested objects.
 */
@Configuration
public class ConfigBeans {

    @Bean
    public LlmConfiguration llmConfiguration(AigenyProperties props) {
        return props.getLlm();
    }

    @Bean
    public DbConfiguration dbConfiguration(AigenyProperties props) {
        return props.getDb();
    }

    @Bean
    public JiraConfiguration jiraConfiguration(AigenyProperties props) {
        return props.getJira();
    }

    @Bean
    public BitbucketConfiguration bitbucketConfiguration(AigenyProperties props) {
        return props.getBitbucket();
    }
}
