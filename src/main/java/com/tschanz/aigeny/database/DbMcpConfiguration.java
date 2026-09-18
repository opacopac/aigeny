package com.tschanz.aigeny.database;

import java.util.Map;

/**
 * Read-only view of the Oracle DB MCP tool configuration: the {@code context}/{@code stage}
 * defaults sent with every MCP tool call, and the optional remote MCP server connection
 * details.
 * <p>
 * Deliberately separate from {@link DbServerConfiguration} (which covers the per-stage JDBC
 * connection details) - a class that only needs the MCP-facing defaults/remote server settings
 * doesn't need to depend on the set of configured stages, and vice versa.
 */
public interface DbMcpConfiguration {

    /**
     * Default value for the mandatory {@code context} argument accepted by every Oracle DB
     * MCP tool call, used when the caller omits it. Currently also the only value accepted -
     * the MCP server returns a "context not available" error for any other value.
     */
    String getDefaultContext();

    /**
     * Default value for the mandatory {@code stage} argument accepted by every Oracle DB MCP
     * tool call, used when the caller omits it. Selects which configured
     * {@link DbServerStage stage} the MCP server connects to.
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


