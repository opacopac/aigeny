package com.tschanz.aigeny.config;

/**
 * Read-only view of the database configuration.
 * <p>
 * Depend on this interface instead of {@link AigenyProperties} to keep
 * database-related classes decoupled from the concrete configuration holder.
 */
public interface DbConfiguration {

    /** JDBC URL, e.g. {@code jdbc:oracle:thin:@hostname:1521/SERVICENAME}. */
    String getUrl();

    /** Database login username. */
    String getUsername();

    /** Database login password. */
    String getPassword();

    /**
     * Returns the effective Oracle schema name.
     * Uses the explicitly configured schema if set; otherwise falls back to
     * the username (in Oracle the username equals the schema by default).
     */
    String getEffectiveSchema();
}
