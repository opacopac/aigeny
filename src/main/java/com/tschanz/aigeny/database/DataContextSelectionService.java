package com.tschanz.aigeny.database;

import jakarta.servlet.http.HttpSession;

/**
 * Resolves the "data context" and "environment stage" currently selected for a chat session -
 * i.e. the same {@code context}/{@code stage} values used as defaults for every Oracle DB MCP
 * tool call (see {@link DbMcpConfiguration#getDefaultContext()} / {@link DbMcpConfiguration#getDefaultStage()}).
 *
 * <p>Today both values are effectively fixed, sourced from {@code aigeny.db.default-context}/
 * {@code aigeny.db.default-stage} in {@code application.yml}. This interface exists so that
 * callers (e.g. {@link com.tschanz.aigeny.orchestration.OrchestrationService}) never need to
 * change once a real per-session selection mechanism is introduced - e.g. a UI dropdown letting
 * the user pick a different context/stage at runtime. {@link #setSelectedContext} and
 * {@link #setSelectedStage} already provide the write side of that future feature; a new
 * controller endpoint calling them is all that would be needed.
 */
public interface DataContextSelectionService {

    /**
     * Returns the data context currently selected for the given session (falls back to the
     * configured default when nothing has been explicitly selected yet).
     */
    String getSelectedContext(HttpSession session);

    /**
     * Returns the environment stage currently selected for the given session (falls back to
     * the configured default when nothing has been explicitly selected yet).
     */
    String getSelectedStage(HttpSession session);

    /**
     * Overrides the data context selected for the given session. Reserved for a future
     * UI-driven selection (e.g. a dropdown); not yet called anywhere in the current codebase.
     */
    void setSelectedContext(HttpSession session, String context);

    /**
     * Overrides the environment stage selected for the given session. Reserved for a future
     * UI-driven selection (e.g. a dropdown); not yet called anywhere in the current codebase.
     */
    void setSelectedStage(HttpSession session, String stage);

    // ── Per-thread activation for tool execution ─────────────────────────────
    //
    // Actual Oracle DB MCP tool calls (see GenericOracleMcpTool) happen deep inside the
    // agentic tool-call loop (OrchestrationService), on the same request-processing thread
    // but without direct access to the HttpSession. Mirroring the ThreadLocal-based
    // ContextProvider pattern already used for the Jira/Bitbucket tokens (see
    // com.tschanz.aigeny.chat.ContextProvider), the session-resolved context/stage is
    // "activated" for the current thread once per request; {@link #getContext()}/
    // {@link #getStage()} are then read from anywhere on that thread without needing the
    // session at all.

    /**
     * Activates the given context/stage for the calling thread, for the duration of a single
     * chat request. Must be paired with a {@link #clear()} call in a {@code finally} block.
     */
    void activate(String context, String stage);

    /**
     * Removes the thread-local activation set by {@link #activate}. Must be called in a
     * {@code finally} block after every {@link #activate} call to prevent memory leaks
     * (e.g. on thread-pool worker threads).
     */
    void clear();

    /**
     * Returns the context activated for the current thread via {@link #activate}, or the
     * configured default (see {@link DbMcpConfiguration#getDefaultContext()}) when nothing
     * has been activated (e.g. outside of a chat request, such as at application startup).
     */
    String getContext();

    /**
     * Returns the stage activated for the current thread via {@link #activate}, or the
     * configured default (see {@link DbMcpConfiguration#getDefaultStage()}) when nothing
     * has been activated (e.g. outside of a chat request, such as at application startup).
     */
    String getStage();
}



