package com.tschanz.aigeny.database;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Default {@link DataContextSelectionService}: reads a per-session override from the
 * {@link HttpSession} (set via {@link #setSelectedContext}/{@link #setSelectedStage}) and
 * falls back to the configured {@link DbMcpConfiguration} defaults when no override exists yet.
 */
@Service
public class ConfiguredDataContextSelectionService implements DataContextSelectionService {

    private static final String SESSION_CONTEXT = "selectedDataContext";
    private static final String SESSION_STAGE   = "selectedEnvironmentStage";

    private static final ThreadLocal<String> ACTIVE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> ACTIVE_STAGE   = new ThreadLocal<>();

    private final DbMcpConfiguration dbMcpConfiguration;

    public ConfiguredDataContextSelectionService(@Qualifier("dbMcpConfiguration") DbMcpConfiguration dbMcpConfiguration) {
        this.dbMcpConfiguration = dbMcpConfiguration;
    }

    @Override
    public String getSelectedContext(HttpSession session) {
        String override = (String) session.getAttribute(SESSION_CONTEXT);
        return (override != null && !override.isBlank()) ? override : dbMcpConfiguration.getDefaultContext();
    }

    @Override
    public String getSelectedStage(HttpSession session) {
        String override = (String) session.getAttribute(SESSION_STAGE);
        return (override != null && !override.isBlank()) ? override : dbMcpConfiguration.getDefaultStage();
    }

    @Override
    public void setSelectedContext(HttpSession session, String context) {
        session.setAttribute(SESSION_CONTEXT, context);
    }

    @Override
    public void setSelectedStage(HttpSession session, String stage) {
        session.setAttribute(SESSION_STAGE, stage);
    }

    // ── Per-thread activation for tool execution ─────────────────────────────

    @Override
    public void activate(String context, String stage) {
        ACTIVE_CONTEXT.set(context);
        ACTIVE_STAGE.set(stage);
    }

    @Override
    public void clear() {
        ACTIVE_CONTEXT.remove();
        ACTIVE_STAGE.remove();
    }

    @Override
    public String getContext() {
        String active = ACTIVE_CONTEXT.get();
        return (active != null && !active.isBlank()) ? active : dbMcpConfiguration.getDefaultContext();
    }

    @Override
    public String getStage() {
        String active = ACTIVE_STAGE.get();
        return (active != null && !active.isBlank()) ? active : dbMcpConfiguration.getDefaultStage();
    }
}

