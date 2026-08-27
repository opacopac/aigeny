package com.tschanz.aigeny.database.mcp_server;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import javax.sql.DataSource;

/**
 * A single tool exposed by the Oracle DB MCP server ({@link OracleMcpServerLauncher}).
 *
 * <p>Each implementation owns exactly one tool: its name, description, JSON parameter
 * schema and the SQL/handling logic for it. See {@link ListTablesHandler},
 * {@link DescribeTableHandler}, {@link SearchSchemaHandler}, {@link SampleTableHandler}
 * and {@link RunQueryHandler}.
 *
 * <p>Implementations are deliberately plain Java (no Spring) since they run inside the
 * framework-free MCP server subprocess - see {@link OracleMcpServerLauncher} for why.
 */
interface OracleMcpToolHandler {

    /** Tool name as seen by the LLM (must be unique and match the client-side tool class). */
    String name();

    /** Human-readable description of what this tool does. */
    String description();

    /** JSON Schema (as a raw string) describing this tool's parameters. */
    String schemaJson();

    /** Executes the tool with the given arguments against the given DataSource. */
    McpSchema.CallToolResult handle(DataSource dataSource, com.fasterxml.jackson.databind.ObjectMapper mapper,
                                     Map<String, Object> arguments);
}

