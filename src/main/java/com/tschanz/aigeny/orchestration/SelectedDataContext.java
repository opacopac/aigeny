package com.tschanz.aigeny.orchestration;

/**
 * The data context / environment stage selected for a chat session at the moment a new
 * conversation starts (see {@link com.tschanz.aigeny.database.DataContextSelectionService}).
 * <p>
 * Passed into {@link OrchestrationService#chat} so it can inject an informational message
 * into the freshly-started conversation, e.g. "User selected data context 'pflege' and
 * environment stage 'INTE'." Kept as a tiny, LLM/servlet-agnostic value object so
 * {@code OrchestrationService} itself never needs to depend on {@code HttpSession}.
 */
public record SelectedDataContext(String context, String stage) {
}

