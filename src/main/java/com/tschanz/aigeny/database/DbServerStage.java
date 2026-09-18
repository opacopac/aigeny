package com.tschanz.aigeny.database;

/**
 * Read-only view of a single configured Oracle DB server/stage's connection details (e.g.
 * {@code INTE}, {@code PROD}, or any other stage name - see {@link DbServerConfiguration#getStages()}).
 */
public interface DbServerStage {

    /** JDBC URL, e.g. {@code jdbc:oracle:thin:@hostname:1521/SERVICENAME}. */
    String getUrl();

    /** Database login username for this stage. */
    String getUsername();

    /** Database login password for this stage. */
    String getPassword();

    /**
     * Optional Oracle schema to set as CURRENT_SCHEMA for the session. When blank,
     * {@link #getUsername()} is used as the schema (Oracle default). See
     * {@link #getEffectiveSchema()} for the resolved value.
     */
    String getSchema();

    /**
     * Returns the effective Oracle schema name for this stage: the explicitly configured
     * schema if set, otherwise falls back to the username (in Oracle the username equals
     * the schema by default).
     */
    String getEffectiveSchema();
}

