/**
 * status-panel.test.js
 *
 * Tests for status-panel.js, organised by the same three-layer structure:
 *
 *  1. Pure state functions – no DOM, no fetch, fully deterministic.
 *  2. StatusPanel class   – DOM elements are plain object mocks.
 *
 * No external dependencies beyond vitest are required.
 */
import { describe, it, expect, beforeEach } from "vitest";
import {
  buildDbState,
  buildTablesState,
  buildJiraState,
  buildBitbucketState,
  buildStatusIndicatorState,
  buildThinkingState,
  StatusPanel,
} from "../../main/resources/static/js/status-panel.js";
// ----------------------------------------------------------------------------
// buildDbState
// ----------------------------------------------------------------------------
describe("buildDbState", () => {
  it("returns ok state when db is configured and its MCP server is connected", () => {
    const s = buildDbState({ dbConfigured: true, dbMcpConnected: true });
    expect(s.className).toBe("info-val ok");
    expect(s.text).toBe("mcp-server connected");
  });
  it("returns error state when db is not configured", () => {
    const s = buildDbState({ dbConfigured: false });
    expect(s.className).toBe("info-val error");
    expect(s.text).toBe("Not configured");
  });
  it("returns error state when db is configured but its MCP server is not connected", () => {
    const s = buildDbState({ dbConfigured: true, dbMcpConnected: false });
    expect(s.className).toBe("info-val error");
  });
  it("exposes the backend error message via title when the MCP connection failed", () => {
    const s = buildDbState({ dbConfigured: true, dbMcpConnected: false, dbMcpError: "MCP client is not connected" });
    expect(s.title).toBe("MCP client is not connected");
  });
  it("empty title when no dbMcpError is provided on failure", () => {
    const s = buildDbState({ dbConfigured: true, dbMcpConnected: false });
    expect(s.title).toBe("");
  });
  it("is a pure function – same input yields same output", () => {
    expect(buildDbState({ dbConfigured: true, dbMcpConnected: true })).toEqual(buildDbState({ dbConfigured: true, dbMcpConnected: true }));
  });
});
// ----------------------------------------------------------------------------
// buildTablesState
// ----------------------------------------------------------------------------
describe("buildTablesState", () => {
  it("returns the table count when list_tables is available", () => {
    const s = buildTablesState({ dbConfigured: true, dbMcpConnected: true, dbMcpListTablesAvailable: true, dbMcpTableCount: 42 });
    expect(s.className).toBe("info-val ok");
    expect(s.text).toBe("42");
  });
  it("returns 'list_tables not available' when the tool is not exposed by the server", () => {
    const s = buildTablesState({ dbConfigured: true, dbMcpConnected: true, dbMcpListTablesAvailable: false });
    expect(s.className).toBe("info-val warn");
    expect(s.text).toBe("list_tables not available");
  });
  it("returns error text when list_tables call failed", () => {
    const s = buildTablesState({ dbConfigured: true, dbMcpConnected: true, dbMcpListTablesAvailable: true, dbMcpTableCount: null, dbMcpError: "boom" });
    expect(s.className).toBe("info-val error");
    expect(s.text).toBe("boom");
  });
  it("returns a placeholder when the MCP server is not connected", () => {
    const s = buildTablesState({ dbConfigured: true, dbMcpConnected: false });
    expect(s.text).not.toBe("42");
  });
});
// ----------------------------------------------------------------------------
// buildJiraState
// ----------------------------------------------------------------------------
describe("buildJiraState", () => {
  it("returns connected state when base-url and token are configured", () => {
    const s = buildJiraState({ jiraBaseUrlConfigured: true, jiraConfigured: true });
    expect(s.className).toBe("info-val ok");
    expect(s.text).toBe("Connected");
    expect(s.btnVisible).toBe(true);
    expect(s.toggleVisible).toBe(true);
    expect(s.btnText).toContain("ändern");
  });
  it("returns token-missing state when base-url configured but no token", () => {
    const s = buildJiraState({ jiraBaseUrlConfigured: true, jiraConfigured: false });
    expect(s.className).toBe("info-val error");
    expect(s.text).toBe("Kein Token");
    expect(s.btnVisible).toBe(true);
    expect(s.toggleVisible).toBe(true);
    expect(s.btnText).toContain("eingeben");
  });
  it("returns not-configured state when base-url is absent", () => {
    const s = buildJiraState({ jiraBaseUrlConfigured: false, jiraConfigured: false });
    expect(s.className).toBe("info-val error");
    expect(s.text).toBe("Not configured");
    expect(s.btnVisible).toBe(false);
    expect(s.toggleVisible).toBe(false);
  });
  it("toggleVisible is false when base-url is absent even if jiraConfigured is true", () => {
    const s = buildJiraState({ jiraBaseUrlConfigured: false, jiraConfigured: true });
    expect(s.toggleVisible).toBe(false);
  });
});
// ----------------------------------------------------------------------------
// buildBitbucketState
// ----------------------------------------------------------------------------
describe("buildBitbucketState", () => {
  it("returns connected state when base-url and token are configured", () => {
    const s = buildBitbucketState({ bitbucketBaseUrlConfigured: true, bitbucketConfigured: true });
    expect(s.className).toBe("info-val ok");
    expect(s.btnVisible).toBe(true);
    expect(s.btnText).toContain("ändern");
  });
  it("returns token-missing state when base-url configured but no token", () => {
    const s = buildBitbucketState({ bitbucketBaseUrlConfigured: true, bitbucketConfigured: false });
    expect(s.className).toBe("info-val error");
    expect(s.btnVisible).toBe(true);
    expect(s.btnText).toContain("eingeben");
  });
  it("returns not-configured state when base-url is absent", () => {
    const s = buildBitbucketState({ bitbucketBaseUrlConfigured: false, bitbucketConfigured: false });
    expect(s.text).toBe("Not configured");
    expect(s.btnVisible).toBe(false);
  });
});
// ----------------------------------------------------------------------------
// buildStatusIndicatorState
// ----------------------------------------------------------------------------
describe("buildStatusIndicatorState", () => {
  it("returns busy when thinking, regardless of hasError", () => {
    expect(buildStatusIndicatorState(true, false).type).toBe("busy");
    expect(buildStatusIndicatorState(true, true).type).toBe("busy");
  });
  it("returns error when not thinking but hasError is true", () => {
    const s = buildStatusIndicatorState(false, true);
    expect(s.type).toBe("error");
    expect(s.text).toContain("nicht erreichbar");
  });
  it("returns ok when not thinking and no error", () => {
    const s = buildStatusIndicatorState(false, false);
    expect(s.type).toBe("ok");
    expect(s.text).toBe("Ready");
  });
});
// ----------------------------------------------------------------------------
// buildThinkingState
// ----------------------------------------------------------------------------
describe("buildThinkingState", () => {
  it("disables and hides send, shows stop when thinking", () => {
    const s = buildThinkingState(true);
    expect(s.sendDisabled).toBe(true);
    expect(s.sendVisible).toBe(false);
    expect(s.stopVisible).toBe(true);
  });
  it("enables and shows send, hides stop when not thinking", () => {
    const s = buildThinkingState(false);
    expect(s.sendDisabled).toBe(false);
    expect(s.sendVisible).toBe(true);
    expect(s.stopVisible).toBe(false);
  });
  it("statusText contains thinking message when thinking", () => {
    expect(buildThinkingState(true).statusText).toContain("mysleet");
  });
  it("statusText contains ready message when not thinking", () => {
    expect(buildThinkingState(false).statusText).toContain("Bereit");
  });
  it("is a pure function", () => {
    expect(buildThinkingState(true)).toEqual(buildThinkingState(true));
  });
});
// ----------------------------------------------------------------------------
// StatusPanel – helpers
// ----------------------------------------------------------------------------
function makeEl(extra = {}) {
  return { textContent: "", className: "", style: { display: "", color: "" }, classList: { add: () => {}, remove: () => {}, toggle: (cls, force) => { } }, disabled: false, ...extra };
}
// classList.toggle mock that actually tracks the class
function makeBtn() {
  const el = makeEl();
  let _visible = false;
  el.classList.toggle = (cls, force) => { _visible = force; };
  el.classList.isVisible = () => _visible;
  return el;
}
function makeElements() {
  return {
    infoLlm:       makeEl(),
    infoModel:     makeEl(),
    infoTables:    makeEl(),
    infoDb:        makeEl(),
    infoJira:      makeEl(),
    btnJiraToken:  makeBtn(),
    jiraWriteRow:  makeEl(),
    infoBitbucket: makeEl(),
    btnBitbucket:  makeBtn(),
    githubInfoRow: makeEl(),
    statusDot:     makeEl(),
    statusText:    makeEl(),
    sendBtn:       makeEl(),
    stopBtn:       makeEl(),
    halStatusText: makeEl(),
    btnCsv:        makeEl(),
  };
}
// ----------------------------------------------------------------------------
// StatusPanel – applyStatus
// ----------------------------------------------------------------------------
describe("StatusPanel – applyStatus()", () => {
  let els, panel;
  beforeEach(() => {
    els   = makeElements();
    panel = new StatusPanel(els);
  });
  it("updates infoLlm textContent", () => {
    panel.applyStatus({ llmProvider: "claude", llmModel: "x", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoLlm.textContent).toBe("claude");
  });
  it("falls back to em-dash when llmProvider is absent", () => {
    panel.applyStatus({ llmModel: "x", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoLlm.textContent).toBe("—");
  });
  it("updates infoModel textContent", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "claude-3", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoModel.textContent).toBe("claude-3");
  });
  it("updates infoTables textContent with the DB MCP table count", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: true, dbMcpConnected: true, dbMcpListTablesAvailable: true, dbMcpTableCount: 42, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoTables.textContent).toBe("42");
  });
  it("shows 'list_tables not available' in infoTables when the tool is missing", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: true, dbMcpConnected: true, dbMcpListTablesAvailable: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoTables.textContent).toBe("list_tables not available");
  });
  it("shows githubInfoRow when provider is github-copilot", () => {
    panel.applyStatus({ llmProvider: "github-copilot", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.githubInfoRow.style.display).toBe("");
  });
  it("hides githubInfoRow when provider is not github-copilot", () => {
    panel.applyStatus({ llmProvider: "claude", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.githubInfoRow.style.display).toBe("none");
  });
  it("applies ok class to infoDb when db is configured and its MCP server is connected", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: true, dbMcpConnected: true, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoDb.className).toBe("info-val ok");
  });
  it("applies error class to infoDb when db is not configured", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoDb.className).toBe("info-val error");
  });
  it("applies error class to infoDb when db is configured but its MCP server is not connected", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: true, dbMcpConnected: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.infoDb.className).toBe("info-val error");
  });
  it("shows jiraWriteRow when jiraBaseUrlConfigured", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: true, jiraConfigured: true, bitbucketBaseUrlConfigured: false });
    expect(els.jiraWriteRow.style.display).toBe("flex");
  });
  it("hides jiraWriteRow when jiraBaseUrlConfigured is false", () => {
    panel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false });
    expect(els.jiraWriteRow.style.display).toBe("none");
  });
  it("does not throw when all elements are null", () => {
    const nullPanel = new StatusPanel({});
    expect(() => nullPanel.applyStatus({ llmProvider: "p", llmModel: "m", dbConfigured: false, jiraBaseUrlConfigured: false, bitbucketBaseUrlConfigured: false })).not.toThrow();
  });
});
// ----------------------------------------------------------------------------
// StatusPanel – setStatusIndicator
// ----------------------------------------------------------------------------
describe("StatusPanel – setStatusIndicator()", () => {
  let els, panel;
  beforeEach(() => { els = makeElements(); panel = new StatusPanel(els); });
  it("sets statusDot className to status-dot + type", () => {
    panel.setStatusIndicator("ok", "Ready");
    expect(els.statusDot.className).toBe("status-dot ok");
  });
  it("sets statusText textContent", () => {
    panel.setStatusIndicator("error", "Server nicht erreichbar");
    expect(els.statusText.textContent).toBe("Server nicht erreichbar");
  });
  it("works for all three types", () => {
    ["ok", "busy", "error"].forEach(type => {
      panel.setStatusIndicator(type, type);
      expect(els.statusDot.className).toBe("status-dot " + type);
    });
  });
});
// ----------------------------------------------------------------------------
// StatusPanel – setThinking
// ----------------------------------------------------------------------------
describe("StatusPanel – setThinking()", () => {
  let els, panel;
  beforeEach(() => { els = makeElements(); panel = new StatusPanel(els); });
  it("disables sendBtn when thinking", () => {
    panel.setThinking(true);
    expect(els.sendBtn.disabled).toBe(true);
  });
  it("hides sendBtn when thinking", () => {
    panel.setThinking(true);
    expect(els.sendBtn.style.display).toBe("none");
  });
  it("shows stopBtn when thinking", () => {
    panel.setThinking(true);
    expect(els.stopBtn.style.display).toBe("");
  });
  it("enables and shows sendBtn when not thinking", () => {
    panel.setThinking(true);
    panel.setThinking(false);
    expect(els.sendBtn.disabled).toBe(false);
    expect(els.sendBtn.style.display).toBe("");
  });
  it("hides stopBtn when not thinking", () => {
    panel.setThinking(true);
    panel.setThinking(false);
    expect(els.stopBtn.style.display).toBe("none");
  });
  it("updates halStatusText while thinking", () => {
    panel.setThinking(true);
    expect(els.halStatusText.textContent).toContain("mysleet");
  });
  it("sets statusDot to busy when thinking", () => {
    panel.setThinking(true);
    expect(els.statusDot.className).toBe("status-dot busy");
  });
  it("sets statusDot to ok when not thinking", () => {
    panel.setThinking(false);
    expect(els.statusDot.className).toBe("status-dot ok");
  });
});
// ----------------------------------------------------------------------------
// StatusPanel – setExportEnabled
// ----------------------------------------------------------------------------
describe("StatusPanel – setExportEnabled()", () => {
  let els, panel;
  beforeEach(() => { els = makeElements(); panel = new StatusPanel(els); });
  it("disables btnCsv when false", () => {
    panel.setExportEnabled(false);
    expect(els.btnCsv.disabled).toBe(true);
  });
  it("enables btnCsv when true", () => {
    panel.setExportEnabled(false);
    panel.setExportEnabled(true);
    expect(els.btnCsv.disabled).toBe(false);
  });
  it("does not throw when btnCsv is null", () => {
    const p = new StatusPanel({ btnCsv: null });
    expect(() => p.setExportEnabled(true)).not.toThrow();
  });
});
