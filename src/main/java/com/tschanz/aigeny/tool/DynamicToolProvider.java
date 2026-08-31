package com.tschanz.aigeny.tool;

import java.util.List;

/**
 * Extension point for components that expose a set of {@link Tool}s to the LLM whose names
 * and count are not known at compile time - typically because they are discovered dynamically
 * from an external system at runtime (e.g. an MCP server's {@code listTools()} response) -
 * as opposed to the normal, fixed {@code @Service}-annotated {@link Tool} beans that Spring
 * collects automatically into a {@code List<Tool>}.
 *
 * <p>{@link com.tschanz.aigeny.orchestration.ToolExecutor} merges the fixed, Spring-managed
 * tools with whatever every {@code DynamicToolProvider} bean currently reports, so new tools
 * appear automatically without any hardcoded client-side tool class.
 *
 * @see com.tschanz.aigeny.database.mcp_client.OracleMcpToolProvider
 */
public interface DynamicToolProvider {

    /** Returns the tools currently available from this provider. May change between calls. */
    List<Tool> getTools();
}

