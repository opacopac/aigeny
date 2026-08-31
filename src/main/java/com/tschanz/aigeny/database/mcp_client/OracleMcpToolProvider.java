package com.tschanz.aigeny.database.mcp_client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.DynamicToolProvider;
import com.tschanz.aigeny.tool.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Dynamically exposes exactly one {@link GenericOracleMcpTool} per tool that the embedded
 * Oracle DB MCP server ({@link OracleMcpConnection}) actually reports via {@code listTools()}.
 *
 * <p>This replaces a fixed set of hand-written {@code *Tool} classes (previously one per
 * server tool: {@code ListTablesTool}, {@code DescribeTableTool}, {@code SearchSchemaTool},
 * {@code SampleTableTool}, {@code RunQueryTool}). The set of tools offered to the LLM is now
 * derived purely from the server's response - adding, removing or renaming a tool on the
 * server side ({@code OracleMcpToolHandler} implementations in the sibling {@code mcp_server}
 * package) requires no client-side code change.
 *
 * <p>Because {@link OracleMcpConnection} performs its (synchronous) startup and tool
 * discovery in its own {@code @PostConstruct}, and this bean depends on that connection via
 * constructor injection, Spring guarantees discovery has already happened (or been attempted)
 * by the time {@link #getTools()} is called - see {@code ToolExecutor}, which merges this
 * provider's tools with the normal, statically-declared {@code Tool} beans.
 */
@Service
public class OracleMcpToolProvider implements DynamicToolProvider {

    private final OracleMcpConnection connection;
    private final ObjectMapper objectMapper;

    public OracleMcpToolProvider(OracleMcpConnection connection, ObjectMapper objectMapper) {
        this.connection = connection;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Tool> getTools() {
        return connection.getDiscoveredToolNames().stream()
                .map(name -> (Tool) new GenericOracleMcpTool(name, connection, objectMapper))
                .toList();
    }
}

