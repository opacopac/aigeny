package com.tschanz.aigeny.database;

import java.util.Map;

/**
 * Read-only view of the database configuration.
 * <p>
 * Depend on this interface instead of {@code AigenyProperties} to keep
 * database-related classes decoupled from the concrete configuration holder.
 * <p>
 * Connection details are organized per <b>stage</b> (e.g. {@code INTE}, {@code PROD}, and
 * potentially any number of additional stages such as {@code TEST}/{@code DEV}) - each stage may
 * have its own JDBC URL, username, password and schema, since different environments are
 * typically entirely separate Oracle instances/accounts. See {@link #getStages()}.
 */
public interface DbConfiguration {

    /** Per-stage Oracle connection details. */
    interface Stage {

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

    /**
     * All configured stages, keyed by stage name (e.g. {@code "INTE"}, {@code "PROD"}, or any
     * other name such as {@code "TEST"}/{@code "DEV"} - fully open-ended, no fixed set of stage
     * names is hardcoded anywhere). Never {@code null} (empty map when none are configured).
     */
    Map<String, ? extends Stage> getStages();

    /**
     * Looks up a configured stage by name, case-insensitively.
     *
     * @return the matching {@link Stage}, or {@code null} if no stage with that name is configured.
     */
    default Stage getStage(String stage) {
        if (stage == null) {
            return null;
        }
        for (Map.Entry<String, ? extends Stage> entry : getStages().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(stage)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Default value for the mandatory {@code context} argument accepted by every Oracle DB
     * MCP tool call, used when the caller omits it. Currently also the only value accepted -
     * the MCP server returns a "context not available" error for any other value.
     */
    String getDefaultContext();

    /**
     * Default value for the mandatory {@code stage} argument accepted by every Oracle DB MCP
     * tool call, used when the caller omits it. Selects which configured {@link Stage} (see
     * {@link #getStages()}) the MCP server connects to.
     */
    String getDefaultStage();

    /**
     * Optional URL of a remote Oracle DB MCP server (e.g. {@code https://mcp-host/mcp}),
     * exposing the Streamable HTTP MCP transport.
     *
     * <p>When blank/not set (the default), the embedded MCP server is spawned locally as a
     * stdio subprocess. When set, the local subprocess is skipped entirely and the client
     * connects to this URL instead - letting the same {@code list_tables}/{@code describe_table}/
     * {@code search_schema}/{@code sample_table}/{@code run_query} tools be served by an
     * independently deployed MCP server without any code change.
     */
    String getMcpServerUrl();

    /**
     * Optional extra HTTP headers to send with every request to a remote Oracle DB MCP
     * server (see {@link #getMcpServerUrl()}) - e.g. an auth header/API key required by
     * that server. Ignored for the local stdio subprocess. Never {@code null} (empty map
     * when none are configured).
     */
    Map<String, String> getMcpServerHeaders();
}

