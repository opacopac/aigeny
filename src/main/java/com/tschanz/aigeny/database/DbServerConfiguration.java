package com.tschanz.aigeny.database;

import java.util.Map;

/**
 * Read-only view of the configured Oracle DB servers/stages.
 * <p>
 * Depend on this interface instead of {@code AigenyProperties} to keep
 * database-related classes decoupled from the concrete configuration holder.
 * <p>
 * Connection details are organized per <b>stage</b> (e.g. {@code INTE}, {@code PROD}, and
 * potentially any number of additional stages such as {@code TEST}/{@code DEV}) - each stage may
 * have its own JDBC URL, username, password and schema, since different environments are
 * typically entirely separate Oracle instances/accounts. See {@link #getStages()}.
 * <p>
 * Deliberately separate from {@link DbMcpConfiguration} (which covers the MCP-tool-facing
 * {@code context}/{@code stage} defaults and the optional remote MCP server settings) - a class
 * that only needs to resolve a stage's connection details doesn't need to depend on the MCP
 * server settings, and vice versa.
 */
public interface DbServerConfiguration {

    /**
     * All configured stages, keyed by stage name (e.g. {@code "INTE"}, {@code "PROD"}, or any
     * other name such as {@code "TEST"}/{@code "DEV"} - fully open-ended, no fixed set of stage
     * names is hardcoded anywhere). Never {@code null} (empty map when none are configured).
     */
    Map<String, ? extends DbServerStage> getStages();

    /**
     * Looks up a configured stage by name, case-insensitively.
     *
     * @return the matching {@link DbServerStage}, or {@code null} if no stage with that name is configured.
     */
    default DbServerStage getStage(String stage) {
        if (stage == null) {
            return null;
        }
        for (Map.Entry<String, ? extends DbServerStage> entry : getStages().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(stage)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

