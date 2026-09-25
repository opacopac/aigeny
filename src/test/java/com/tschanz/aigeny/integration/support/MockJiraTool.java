package com.tschanz.aigeny.integration.support;

/**
 * Mock of a Jira MCP tool (e.g. {@code query_jira}, {@code create_issue}, {@code update_issue}).
 * Useful for integration tests that need to simulate Jira read/write interactions without a real
 * Jira connection.
 */
public class MockJiraTool extends GenericMockTool {

    public MockJiraTool(String name) {
        super(name, "Mock Jira tool: " + name);
    }
}

