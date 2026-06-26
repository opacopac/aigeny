package com.tschanz.aigeny.config;

/**
 * Read-only view of the Jira configuration.
 * <p>
 * Depend on this interface instead of {@link AigenyProperties} to keep
 * Jira-related classes decoupled from the concrete configuration holder.
 */
public interface JiraConfiguration {

    /** Jira base URL, e.g. {@code https://jira.example.com}. */
    String getBaseUrl();

    /** API token (Personal Access Token) – may be empty if not configured server-side. */
    String getToken();
}
