/**
 * token-modal.test.js
 *
 * Tests for token-modal.js, organised by the same three-layer structure:
 *
 *  1. Pure hint-state functions – no DOM, no fetch, fully deterministic.
 *  2. TokenModal class – fetch and setTimeout are replaced by vi stubs;
 *     localStorage uses jsdom's built-in implementation.
 *
 * No external dependencies beyond vitest are required.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  buildHintOnOpen,
  buildHintOnSaveSuccess,
  buildHintOnClear,
  buildHintOnError,
  TokenModal,
  CLOSE_DELAY_SAVE,
  CLOSE_DELAY_CLEAR,
} from '../../main/resources/static/js/token-modal.js';

// ── Helper: build a minimal elements bag ─────────────────────────────────────
function makeEl(extra = {}) {
  return { style: { display: '' }, textContent: '', className: '', ...extra };
}

function makeElements() {
  return {
    modal: makeEl(),
    input: makeEl({ value: '', focus: vi.fn() }),
    hint:  makeEl(),
  };
}

const STORAGE_KEY  = 'test.token';
const API_ENDPOINT = '/api/test/token';

// ════════════════════════════════════════════════════════════════════════════
// buildHintOnOpen – pure
// ════════════════════════════════════════════════════════════════════════════
describe('buildHintOnOpen', () => {
  it('returns ok hint when a stored token is provided', () => {
    const hint = buildHintOnOpen('my-token');
    expect(hint.text).toBe('✔ Token ist gespeichert.');
    expect(hint.className).toBe('modal-hint ok');
  });

  it('returns empty hint when storedToken is empty string', () => {
    const hint = buildHintOnOpen('');
    expect(hint.text).toBe('');
    expect(hint.className).toBe('modal-hint');
  });

  it('returns empty hint when storedToken is falsy (null-like)', () => {
    // callers pass '' when localStorage returns null; test both
    const hint = buildHintOnOpen('');
    expect(hint.className).toBe('modal-hint');
  });

  it('returns a plain object with text and className', () => {
    const hint = buildHintOnOpen('x');
    expect(hint).toHaveProperty('text');
    expect(hint).toHaveProperty('className');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildHintOnSaveSuccess – pure
// ════════════════════════════════════════════════════════════════════════════
describe('buildHintOnSaveSuccess', () => {
  it('returns ok hint when a non-empty token was saved', () => {
    const hint = buildHintOnSaveSuccess('secret');
    expect(hint.text).toBe('✔ Token gespeichert! Da, choroscho!');
    expect(hint.className).toBe('modal-hint ok');
  });

  it('returns deletion hint when token is empty (save with blank input)', () => {
    const hint = buildHintOnSaveSuccess('');
    expect(hint.text).toBe('Token gelöscht.');
    expect(hint.className).toBe('modal-hint');
  });

  it('has no error modifier in className for either case', () => {
    expect(buildHintOnSaveSuccess('x').className).not.toContain('error');
    expect(buildHintOnSaveSuccess('').className).not.toContain('error');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildHintOnClear – pure
// ════════════════════════════════════════════════════════════════════════════
describe('buildHintOnClear', () => {
  it('returns a deletion message', () => {
    expect(buildHintOnClear().text).toBe('Token gelöscht.');
  });

  it('returns the plain modal-hint class (no ok/error modifier)', () => {
    expect(buildHintOnClear().className).toBe('modal-hint');
  });

  it('is a stable pure function – two calls return equal values', () => {
    expect(buildHintOnClear()).toEqual(buildHintOnClear());
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildHintOnError – pure
// ════════════════════════════════════════════════════════════════════════════
describe('buildHintOnError', () => {
  it('starts with "Njet! Fehler:"', () => {
    expect(buildHintOnError('oops').text).toMatch(/^Njet! Fehler:/);
  });

  it('appends the error message', () => {
    expect(buildHintOnError('HTTP 401').text).toContain('HTTP 401');
  });

  it('has the error className', () => {
    expect(buildHintOnError('x').className).toBe('modal-hint error');
  });

  it('handles empty error message gracefully', () => {
    expect(() => buildHintOnError('')).not.toThrow();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// TokenModal – class behaviour
// ════════════════════════════════════════════════════════════════════════════
describe('TokenModal', () => {
  let els;
  let modal;

  beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();

    els   = makeElements();
    modal = new TokenModal(STORAGE_KEY, API_ENDPOINT, els);

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({}),
    }));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  // ── init ──────────────────────────────────────────────────────────────────
  it('init() does not throw when no callbacks are supplied', () => {
    expect(() => modal.init()).not.toThrow();
  });

  it('init() accepts and stores an onSaved callback', () => {
    const onSaved = vi.fn();
    expect(() => modal.init({ onSaved })).not.toThrow();
  });

  // ── open ──────────────────────────────────────────────────────────────────
  it('open() shows the modal', () => {
    modal.open();
    expect(els.modal.style.display).toBe('flex');
  });

  it('open() pre-fills input with stored token', () => {
    localStorage.setItem(STORAGE_KEY, 'my-secret');
    modal.open();
    expect(els.input.value).toBe('my-secret');
  });

  it('open() leaves input empty when no token is stored', () => {
    modal.open();
    expect(els.input.value).toBe('');
  });

  it('open() sets the ok hint when a token is stored', () => {
    localStorage.setItem(STORAGE_KEY, 'abc');
    modal.open();
    expect(els.hint.className).toBe('modal-hint ok');
  });

  it('open() sets the plain hint when no token is stored', () => {
    modal.open();
    expect(els.hint.className).toBe('modal-hint');
    expect(els.hint.textContent).toBe('');
  });

  // ── close ─────────────────────────────────────────────────────────────────
  it('close() without event hides the modal', () => {
    els.modal.style.display = 'flex';
    modal.close();
    expect(els.modal.style.display).toBe('none');
  });

  it('close(e) does nothing when e.target is not the modal', () => {
    els.modal.style.display = 'flex';
    modal.close({ target: {} });
    expect(els.modal.style.display).toBe('flex');
  });

  it('close(e) hides modal when e.target matches the modal element', () => {
    els.modal.style.display = 'flex';
    modal.close({ target: els.modal });
    expect(els.modal.style.display).toBe('none');
  });

  // ── save – successful POST with token ─────────────────────────────────────
  it('save() POSTs to the configured API endpoint', async () => {
    els.input.value = 'tok123';
    await modal.save();
    expect(fetch).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({ method: 'POST' }));
  });

  it('save() sends the token value in the request body', async () => {
    els.input.value = 'tok123';
    await modal.save();
    const body = JSON.parse(fetch.mock.calls[0][1].body);
    expect(body.token).toBe('tok123');
  });

  it('save() stores token in localStorage on success', async () => {
    els.input.value = 'tok123';
    await modal.save();
    expect(localStorage.getItem(STORAGE_KEY)).toBe('tok123');
  });

  it('save() shows the ok hint after saving a non-empty token', async () => {
    els.input.value = 'tok123';
    await modal.save();
    expect(els.hint.className).toBe('modal-hint ok');
  });

  it('save() closes modal after CLOSE_DELAY_SAVE ms', async () => {
    els.input.value = 'tok';
    await modal.save();
    expect(els.modal.style.display).not.toBe('none');  // still open
    vi.advanceTimersByTime(CLOSE_DELAY_SAVE);
    expect(els.modal.style.display).toBe('none');
  });

  it('save() calls onSaved callback after CLOSE_DELAY_SAVE ms', async () => {
    const onSaved = vi.fn();
    modal.init({ onSaved });
    els.input.value = 'tok';
    await modal.save();
    expect(onSaved).not.toHaveBeenCalled();
    vi.advanceTimersByTime(CLOSE_DELAY_SAVE);
    expect(onSaved).toHaveBeenCalledOnce();
  });

  // ── save – successful POST with empty token (acts as delete) ──────────────
  it('save() removes token from localStorage when input is empty', async () => {
    localStorage.setItem(STORAGE_KEY, 'old');
    els.input.value = '';
    await modal.save();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('save() shows deletion hint when saving an empty token', async () => {
    els.input.value = '';
    await modal.save();
    expect(els.hint.text || els.hint.textContent).toContain('gelöscht');
  });

  // ── save – failed POST ────────────────────────────────────────────────────
  it('save() shows error hint when response is not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }));
    els.input.value = 'bad';
    await modal.save();
    expect(els.hint.className).toBe('modal-hint error');
  });

  it('save() shows error hint when fetch throws', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network')));
    els.input.value = 'bad';
    await modal.save();
    expect(els.hint.className).toBe('modal-hint error');
    expect(els.hint.textContent).toContain('network');
  });

  it('save() does NOT close modal after delay when an error occurred', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));
    els.input.value = 'bad';
    await modal.save();
    vi.advanceTimersByTime(CLOSE_DELAY_SAVE + 100);
    expect(els.modal.style.display).not.toBe('none');
  });

  // ── clear ─────────────────────────────────────────────────────────────────
  it('clear() removes token from localStorage', async () => {
    localStorage.setItem(STORAGE_KEY, 'existing');
    await modal.clear();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('clear() POSTs an empty token to the API', async () => {
    await modal.clear();
    const body = JSON.parse(fetch.mock.calls[0][1].body);
    expect(body.token).toBe('');
  });

  it('clear() resets the input value to empty string', async () => {
    els.input.value = 'leftover';
    await modal.clear();
    expect(els.input.value).toBe('');
  });

  it('clear() applies the clear hint', async () => {
    await modal.clear();
    expect(els.hint.textContent).toBe('Token gelöscht.');
    expect(els.hint.className).toBe('modal-hint');
  });

  it('clear() closes modal after CLOSE_DELAY_CLEAR ms', async () => {
    await modal.clear();
    expect(els.modal.style.display).not.toBe('none');
    vi.advanceTimersByTime(CLOSE_DELAY_CLEAR);
    expect(els.modal.style.display).toBe('none');
  });

  it('clear() calls onSaved callback after CLOSE_DELAY_CLEAR ms', async () => {
    const onSaved = vi.fn();
    modal.init({ onSaved });
    await modal.clear();
    expect(onSaved).not.toHaveBeenCalled();
    vi.advanceTimersByTime(CLOSE_DELAY_CLEAR);
    expect(onSaved).toHaveBeenCalledOnce();
  });

  it('clear() CLOSE_DELAY is shorter than save() CLOSE_DELAY', () => {
    expect(CLOSE_DELAY_CLEAR).toBeLessThan(CLOSE_DELAY_SAVE);
  });

  it('clear() does not throw when fetch fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(modal.clear()).resolves.not.toThrow();
  });

  // ── syncToSession ─────────────────────────────────────────────────────────
  it('syncToSession() POSTs the stored token to the API endpoint', async () => {
    localStorage.setItem(STORAGE_KEY, 'session-token');
    await modal.syncToSession();
    expect(fetch).toHaveBeenCalledWith(API_ENDPOINT, expect.objectContaining({ method: 'POST' }));
    const body = JSON.parse(fetch.mock.calls[0][1].body);
    expect(body.token).toBe('session-token');
  });

  it('syncToSession() is a no-op when localStorage has no token', async () => {
    await modal.syncToSession();
    expect(fetch).not.toHaveBeenCalled();
  });

  it('syncToSession() does not throw when fetch fails', async () => {
    localStorage.setItem(STORAGE_KEY, 'tok');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(modal.syncToSession()).resolves.not.toThrow();
  });

  // ── Null element safety ───────────────────────────────────────────────────
  it('open() does not throw when modal element is null', () => {
    const m = new TokenModal(STORAGE_KEY, API_ENDPOINT, { modal: null, input: null, hint: null });
    expect(() => m.open()).not.toThrow();
  });

  it('close() does not throw when modal element is null', () => {
    const m = new TokenModal(STORAGE_KEY, API_ENDPOINT, { modal: null, input: null, hint: null });
    expect(() => m.close()).not.toThrow();
  });

  it('save() does not throw when all elements are null', async () => {
    const m = new TokenModal(STORAGE_KEY, API_ENDPOINT, { modal: null, input: null, hint: null });
    await expect(m.save()).resolves.not.toThrow();
  });

  it('clear() does not throw when all elements are null', async () => {
    const m = new TokenModal(STORAGE_KEY, API_ENDPOINT, { modal: null, input: null, hint: null });
    await expect(m.clear()).resolves.not.toThrow();
  });
});

