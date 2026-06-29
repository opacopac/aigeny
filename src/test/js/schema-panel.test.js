/**
 * schema-panel.test.js
 *
 * Tests for schema-panel.js, following the same three-layer structure used
 * throughout the project:
 *
 *  1. buildSchemaButtonState  – pure function, no DOM, fully deterministic.
 *  2. SchemaPanel.reload()    – fetch is replaced by a vi spy; DOM elements
 *                               are minimal plain-object mocks.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buildSchemaButtonState, SchemaPanel } from '../../main/resources/static/js/schema-panel.js';

// ── Helper: minimal element mocks ────────────────────────────────────────────

function makeElements() {
  return {
    btn:        { disabled: false, textContent: '↻ Schema neu laden' },
    infoTables: { textContent: '0' },
  };
}

// ════════════════════════════════════════════════════════════════════════════
// buildSchemaButtonState – pure
// ════════════════════════════════════════════════════════════════════════════

describe('buildSchemaButtonState', () => {
  it('returns disabled=true while loading', () => {
    expect(buildSchemaButtonState(true).disabled).toBe(true);
  });

  it('returns disabled=false when not loading', () => {
    expect(buildSchemaButtonState(false).disabled).toBe(false);
  });

  it('returns loading text while loading', () => {
    expect(buildSchemaButtonState(true).text).toContain('⌛');
  });

  it('returns default button text when not loading', () => {
    expect(buildSchemaButtonState(false).text).toContain('↻');
  });

  it('loading and idle states have different text', () => {
    expect(buildSchemaButtonState(true).text).not.toBe(buildSchemaButtonState(false).text);
  });

  it('is a pure function – same input always yields equal output', () => {
    expect(buildSchemaButtonState(true)).toEqual(buildSchemaButtonState(true));
    expect(buildSchemaButtonState(false)).toEqual(buildSchemaButtonState(false));
  });
});

// ════════════════════════════════════════════════════════════════════════════
// SchemaPanel – reload()
// ════════════════════════════════════════════════════════════════════════════

describe('SchemaPanel – reload()', () => {
  let fetchSpy;
  let panel;
  let els;
  let appendFn;

  beforeEach(() => {
    els      = makeElements();
    appendFn = vi.fn();
    panel    = new SchemaPanel(els, '/api/test/schema/reload');

    fetchSpy = vi.spyOn(globalThis, 'fetch');
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // ── Button state management ─────────────────────────────────────────────

  it('disables the button while the request is in flight', async () => {
    let resolveReload;
    fetchSpy.mockReturnValue(new Promise(resolve => { resolveReload = resolve; }));

    const reloadPromise = panel.reload(appendFn);
    expect(els.btn.disabled).toBe(true);

    resolveReload({ json: async () => ({ status: 'ok', tables: '42' }) });
    await reloadPromise;
  });

  it('re-enables the button after a successful reload', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '7' }) });

    await panel.reload(appendFn);

    expect(els.btn.disabled).toBe(false);
  });

  it('re-enables the button after a failed reload (non-ok status)', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'error', message: 'oops' }) });

    await panel.reload(appendFn);

    expect(els.btn.disabled).toBe(false);
  });

  it('re-enables the button after a network error', async () => {
    fetchSpy.mockRejectedValue(new Error('network'));

    await panel.reload(appendFn);

    expect(els.btn.disabled).toBe(false);
  });

  it('restores the default button text after completion', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '5' }) });

    await panel.reload(appendFn);

    expect(els.btn.textContent).toContain('↻');
  });

  // ── Fetch call ────────────────────────────────────────────────────────────

  it('POSTs to the configured API endpoint', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '3' }) });

    await panel.reload(appendFn);

    expect(fetchSpy).toHaveBeenCalledWith('/api/test/schema/reload', { method: 'POST' });
  });

  // ── infoTables update ─────────────────────────────────────────────────────

  it('updates infoTables.textContent with the returned table count on success', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '99' }) });

    await panel.reload(appendFn);

    expect(els.infoTables.textContent).toBe('99');
  });

  it('does not update infoTables on error response', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'error', message: 'fail' }) });

    await panel.reload(appendFn);

    expect(els.infoTables.textContent).toBe('0'); // unchanged
  });

  // ── appendMessageFn calls ─────────────────────────────────────────────────

  it('calls appendMessageFn with role "aigeny" on success', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '5' }) });

    await panel.reload(appendFn);

    expect(appendFn).toHaveBeenCalledWith('aigeny', expect.stringContaining('5'));
  });

  it('success message mentions the table count', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '42' }) });

    await panel.reload(appendFn);

    expect(appendFn).toHaveBeenCalledWith('aigeny', expect.stringContaining('42'));
  });

  it('calls appendMessageFn with error text on non-ok backend status', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'error', message: 'DB down' }) });

    await panel.reload(appendFn);

    expect(appendFn).toHaveBeenCalledWith('aigeny', expect.stringContaining('DB down'));
  });

  it('calls appendMessageFn with a fallback message when backend provides no message', async () => {
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'error' }) });

    await panel.reload(appendFn);

    expect(appendFn).toHaveBeenCalledWith('aigeny', expect.stringContaining('unbekannter Fehler'));
  });

  it('calls appendMessageFn with error text on network failure', async () => {
    fetchSpy.mockRejectedValue(new Error('network gone'));

    await panel.reload(appendFn);

    expect(appendFn).toHaveBeenCalledWith('aigeny', expect.stringContaining('network gone'));
  });

  // ── Null-safety ───────────────────────────────────────────────────────────

  it('does not throw when btn element is null', async () => {
    const nullEls = { btn: null, infoTables: makeElements().infoTables };
    const p = new SchemaPanel(nullEls, '/api/test/schema/reload');
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '1' }) });

    await expect(p.reload(appendFn)).resolves.not.toThrow();
  });

  it('does not throw when infoTables element is null', async () => {
    const nullEls = { btn: makeElements().btn, infoTables: null };
    const p = new SchemaPanel(nullEls, '/api/test/schema/reload');
    fetchSpy.mockResolvedValue({ json: async () => ({ status: 'ok', tables: '1' }) });

    await expect(p.reload(appendFn)).resolves.not.toThrow();
  });
});

