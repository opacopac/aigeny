package com.tschanz.aigeny.database;

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

    /**
     * Optional URL of a remote Oracle DB MCP server (e.g. {@code http://mcp-host:8081}),
     * exposing the SSE-based MCP HTTP transport.
     *
     * <p>When blank/not set (the default), the embedded MCP server is spawned locally as a
     * stdio subprocess. When set, the local subprocess is skipped entirely and the client
     * connects to this URL instead - letting the same {@code list_tables}/{@code describe_table}/
     * {@code search_schema}/{@code sample_table}/{@code run_query} tools be served by an
     * independently deployed MCP server without any code change.
     */
    String getMcpServerUrl();
}
