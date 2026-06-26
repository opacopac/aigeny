package com.tschanz.aigeny.config;

/**
 * Read-only view of the Bitbucket configuration.
 * <p>
 * Depend on this interface instead of {@link AigenyProperties} to keep
 * Bitbucket-related classes decoupled from the concrete configuration holder.
 */
public interface BitbucketConfiguration {

    /** Bitbucket Server base URL, e.g. {@code https://code.example.com}. */
    String getBaseUrl();

    /** Personal Access Token – may be empty if not configured server-side. */
    String getToken();
}
