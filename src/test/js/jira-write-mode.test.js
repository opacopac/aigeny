/**
 * write-mode.test.js
 *
 * Tests for write-mode.js, organised by the same three-layer structure used
 * throughout the project:
 *
 *  1. buildWriteToggleState – pure function, no DOM, fully deterministic.
 *  2. JiraWriteMode class   – fetch is replaced by a vi spy; localStorage uses
 *     jsdom's built-in implementation.
 *
 * No external dependencies beyond vitest are required.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  buildWriteToggleState,
  JiraWriteMode,
} from '../../main/resources/static/js/jira-write-mode.js';

// ── Helper: minimal element mocks ────────────────────────────────────────────
function makeElements() {
  return {
    toggle: { checked: false },
    label:  { textContent: '', style: { color: '' } },
  };
}

const STORAGE_KEY  = 'test.writeEnabled';
const API_ENDPOINT = '/api/test/write-mode';

// ════════════════════════════════════════════════════════════════════════════
// buildWriteToggleState – pure
// ════════════════════════════════════════════════════════════════════════════
describe('buildWriteToggleState', () => {
  it('returns checked=true when enabled', () => {
    expect(buildWriteToggleState(true).checked).toBe(true);
  });

  it('returns checked=false when disabled', () => {
    expect(buildWriteToggleState(false).checked).toBe(false);
  });

  it('labelText contains "ein" when enabled', () => {
    expect(buildWriteToggleState(true).labelText).toContain('ein');
  });

  it('labelText contains "aus" when disabled', () => {
    expect(buildWriteToggleState(false).labelText).toContain('aus');
  });

  it('labelText does NOT contain "aus" when enabled', () => {
    expect(buildWriteToggleState(true).labelText).not.toContain('aus');
  });

  it('labelText does NOT contain "ein" when disabled', () => {
    // "ein" must not appear inside "aus" or the label when disabled
    // The label reads "(aus)" so "ein" should be absent
    const label = buildWriteToggleState(false).labelText;
    // strip "Schreiben" to avoid false match on "ein" in "Schreiben"
    const parens = label.match(/\(([^)]+)\)/)?.[1] ?? '';
    expect(parens).toBe('aus');
  });

  it('labelColor is var(--red) when enabled', () => {
    expect(buildWriteToggleState(true).labelColor).toBe('var(--red)');
  });

  it('labelColor is empty string when disabled', () => {
    expect(buildWriteToggleState(false).labelColor).toBe('');
  });

  it('labelText includes the pencil symbol ✏ regardless of state', () => {
    expect(buildWriteToggleState(true).labelText).toContain('✏');
    expect(buildWriteToggleState(false).labelText).toContain('✏');
  });

  it('is a pure function – same input always yields equal output', () => {
    expect(buildWriteToggleState(true)).toEqual(buildWriteToggleState(true));
    expect(buildWriteToggleState(false)).toEqual(buildWriteToggleState(false));
  });

  it('enabled and disabled states produce different labelText', () => {
    expect(buildWriteToggleState(true).labelText).not.toBe(
      buildWriteToggleState(false).labelText
    );
  });
});

// ════════════════════════════════════════════════════════════════════════════
// JiraWriteMode – toggle()
// ════════════════════════════════════════════════════════════════════════════
describe('JiraWriteMode – toggle()', () => {
  let fetchSpy;
  let wm;
  let els;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true });
    localStorage.clear();
    els = makeElements();
    wm  = new JiraWriteMode(STORAGE_KEY, API_ENDPOINT, els);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('POSTs to the API endpoint with enabled: true', async () => {
    await wm.toggle(true);
    expect(fetchSpy).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({
      method: 'POST',
      body:   JSON.stringify({ enabled: true }),
    }));
  });

  it('POSTs to the API endpoint with enabled: false', async () => {
    await wm.toggle(false);
    expect(fetchSpy).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({
      body: JSON.stringify({ enabled: false }),
    }));
  });

  it('sends Content-Type: application/json header', async () => {
    await wm.toggle(true);
    expect(fetchSpy).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({
      headers: { 'Content-Type': 'application/json' },
    }));
  });

  it('stores "true" in localStorage when enabled', async () => {
    await wm.toggle(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('true');
  });

  it('stores "false" in localStorage when disabled', async () => {
    await wm.toggle(false);
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false');
  });

  it('sets toggle.checked = true when enabled', async () => {
    await wm.toggle(true);
    expect(els.toggle.checked).toBe(true);
  });

  it('sets toggle.checked = false when disabled', async () => {
    els.toggle.checked = true;
    await wm.toggle(false);
    expect(els.toggle.checked).toBe(false);
  });

  it('updates label.textContent to the "ein" variant', async () => {
    await wm.toggle(true);
    expect(els.label.textContent).toContain('ein');
  });

  it('updates label.style.color to var(--red) when enabled', async () => {
    await wm.toggle(true);
    expect(els.label.style.color).toBe('var(--red)');
  });

  it('clears label.style.color when disabled', async () => {
    els.label.style.color = 'var(--red)';
    await wm.toggle(false);
    expect(els.label.style.color).toBe('');
  });

  it('calls onToggled callback with true when enabled', async () => {
    const cb = vi.fn();
    wm.init({ onToggled: cb });
    await wm.toggle(true);
    expect(cb).toHaveBeenCalledWith(true);
  });

  it('calls onToggled callback with false when disabled', async () => {
    const cb = vi.fn();
    wm.init({ onToggled: cb });
    await wm.toggle(false);
    expect(cb).toHaveBeenCalledWith(false);
  });

  it('does not throw when fetch rejects (network error)', async () => {
    fetchSpy.mockRejectedValue(new Error('network'));
    await expect(wm.toggle(true)).resolves.not.toThrow();
  });

  it('does not update localStorage when fetch rejects', async () => {
    fetchSpy.mockRejectedValue(new Error('network'));
    await wm.toggle(true);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('works gracefully when toggle element is null', async () => {
    const nullEls = { toggle: null, label: makeElements().label };
    const wmNull  = new JiraWriteMode(STORAGE_KEY, API_ENDPOINT, nullEls);
    await expect(wmNull.toggle(true)).resolves.not.toThrow();
  });

  it('works gracefully when label element is null', async () => {
    const nullEls = { toggle: makeElements().toggle, label: null };
    const wmNull  = new JiraWriteMode(STORAGE_KEY, API_ENDPOINT, nullEls);
    await expect(wmNull.toggle(true)).resolves.not.toThrow();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// JiraWriteMode – syncToSession()
// ════════════════════════════════════════════════════════════════════════════
describe('JiraWriteMode – syncToSession()', () => {
  let fetchSpy;
  let wm;
  let els;

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true });
    localStorage.clear();
    els = makeElements();
    wm  = new JiraWriteMode(STORAGE_KEY, API_ENDPOINT, els);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('always stores "false" in localStorage regardless of prior value', async () => {
    localStorage.setItem(STORAGE_KEY, 'true');
    await wm.syncToSession();
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false');
  });

  it('POSTs enabled: false to the API', async () => {
    await wm.syncToSession();
    expect(fetchSpy).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({
      body: JSON.stringify({ enabled: false }),
    }));
  });

  it('resets toggle.checked to false', async () => {
    els.toggle.checked = true;
    await wm.syncToSession();
    expect(els.toggle.checked).toBe(false);
  });

  it('resets label to the disabled variant', async () => {
    await wm.syncToSession();
    expect(els.label.textContent).toContain('aus');
  });

  it('does not throw when fetch rejects', async () => {
    fetchSpy.mockRejectedValue(new Error('network'));
    await expect(wm.syncToSession()).resolves.not.toThrow();
  });

  it('still resets localStorage even when fetch rejects', async () => {
    fetchSpy.mockRejectedValue(new Error('network'));
    localStorage.setItem(STORAGE_KEY, 'true');
    await wm.syncToSession();
    expect(localStorage.getItem(STORAGE_KEY)).toBe('false');
  });
});

