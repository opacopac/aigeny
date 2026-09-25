package com.tschanz.aigeny.integration.support;

/**
 * Mock of a Bitbucket MCP tool (e.g. {@code search_bitbucket}, {@code read_bitbucket_file}).
 * Useful for integration tests that need to simulate Bitbucket interactions without a real
 * Bitbucket connection.
 */
public class MockBitbucketTool extends GenericMockTool {

    public MockBitbucketTool(String name) {
        super(name, "Mock Bitbucket tool: " + name);
    }
}

