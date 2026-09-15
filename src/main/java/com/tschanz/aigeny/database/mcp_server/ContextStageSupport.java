package com.tschanz.aigeny.database.mcp_server;

import io.modelcontextprotocol.spec.McpSchema;

import java.util.Map;
import javax.sql.DataSource;

/**
 * Validates the mandatory {@code context}/{@code stage} arguments that every
 * {@link OracleMcpToolHandler} tool call carries and resolves the {@link DataSource} to use.
 *
 * <p>{@code context} currently only accepts a single configured value ({@code aigeny.db.default-context},
 * e.g. {@code "pflege"}) - any other value is rejected with a "context not available" error, laying
 * the groundwork for supporting multiple contexts later without changing the tool contract.
 *
 * <p>{@code stage} selects which JDBC connection is used: {@code INTE} or {@code PROD}. Only stages
 * for which a JDBC URL was actually configured (see {@link OracleMcpServerLauncher}) are available;
 * any other value (or a configured-but-unreachable stage) is rejected with a "stage not available"
 * error.
 *
 * <p>Both arguments are optional on the wire (the caller may omit them) and fall back to the
 * configured defaults - but are declared {@code required} in each handler's JSON schema so the LLM
 * is nudged to always pass them explicitly.
 */
final class ContextStageSupport {

    private ContextStageSupport() {}

    /** Outcome of resolving {@code context}/{@code stage}: either a usable {@link DataSource}, or an error result. */
    record Resolution(DataSource dataSource, McpSchema.CallToolResult error) {
        boolean isError() { return error != null; }
    }

    static Resolution resolve(Map<String, Object> arguments, String allowedContext,
                               String defaultContext, String defaultStage,
                               Map<String, DataSource> stageDataSources) {
        String context = OracleSqlSupport.stringArg(arguments, "context");
        if (context == null || context.isBlank()) {
            context = defaultContext;
        }
        if (allowedContext == null || allowedContext.isBlank() || !allowedContext.equalsIgnoreCase(context)) {
            return new Resolution(null, OracleSqlSupport.errorResult(
                    "ERROR: context not available: '" + context + "'."));
        }

        String stage = OracleSqlSupport.stringArg(arguments, "stage");
        if (stage == null || stage.isBlank()) {
            stage = defaultStage;
        }
        DataSource dataSource = stageDataSources.get(stage.toUpperCase());
        if (dataSource == null) {
            return new Resolution(null, OracleSqlSupport.errorResult(
                    "ERROR: stage not available: '" + stage + "'."));
        }

        return new Resolution(dataSource, null);
    }
}

