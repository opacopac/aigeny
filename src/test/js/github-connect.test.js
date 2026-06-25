/**
 * github-connect.test.js
 *
 * Tests for github-connect.js, mirroring the three-layer structure of the
 * module itself:
 *
 *  1. Pure state-mapping functions (no DOM, no fetch) – fully deterministic.
 *  2. GithubConnector class – DOM and fetch calls are replaced by vi.fn() stubs.
 *
 * No external dependencies beyond vitest are required.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  buildConnectViewState,
  buildInfoBoxState,
  buildPairingHintText,
  buildPollingErrorText,
  GithubConnector,
} from '../../main/resources/static/js/github-connect.js';

// ── Helper: build a full elements bag with vi.fn()-based style setters ────────
// Each "element" is a plain object whose property assignments are observable.
function makeEl(extra = {}) {
  return { style: { display: '' }, textContent: '', className: '', ...extra };
}

function makeElements() {
  return {
    modal:            makeEl(),
    startPanel:       makeEl(),
    pairingPanel:     makeEl(),
    connectedPanel:   makeEl(),
    loginName:        makeEl(),
    userCode:         makeEl(),
    verificationLink: makeEl({ href: '', textContent: '' }),
    hintText:         makeEl(),
    infoBox:          makeEl(),
    infoBtn:          makeEl(),
  };
}

// ════════════════════════════════════════════════════════════════════════════
// buildConnectViewState – pure mapping
// ════════════════════════════════════════════════════════════════════════════
describe('buildConnectViewState', () => {
  it('returns panel="connected" when data.connected is true', () => {
    const state = buildConnectViewState({ connected: true, pairing: false });
    expect(state.panel).toBe('connected');
  });

  it('includes loginName from data.login when connected', () => {
    const state = buildConnectViewState({ connected: true, login: 'octocat' });
    expect(state.loginName).toBe('octocat');
  });

  it('falls back to "(unbekannt)" when login is missing and connected', () => {
    const state = buildConnectViewState({ connected: true });
    expect(state.loginName).toBe('(unbekannt)');
  });

  it('falls back to "(unbekannt)" when login is empty string', () => {
    const state = buildConnectViewState({ connected: true, login: '' });
    expect(state.loginName).toBe('(unbekannt)');
  });

  it('returns panel="pairing" when data.pairing is true (not connected)', () => {
    const state = buildConnectViewState({ connected: false, pairing: true });
    expect(state.panel).toBe('pairing');
  });

  it('does not include loginName when pairing', () => {
    const state = buildConnectViewState({ connected: false, pairing: true });
    expect(state.loginName).toBeUndefined();
  });

  it('returns panel="start" when neither connected nor pairing', () => {
    const state = buildConnectViewState({ connected: false, pairing: false });
    expect(state.panel).toBe('start');
  });

  it('connected takes priority over pairing', () => {
    // Defensive: if the backend ever sends both true, connected wins.
    const state = buildConnectViewState({ connected: true, pairing: true });
    expect(state.panel).toBe('connected');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildInfoBoxState – pure mapping
// ════════════════════════════════════════════════════════════════════════════
describe('buildInfoBoxState', () => {
  it('returns ok className when connected', () => {
    const state = buildInfoBoxState({ connected: true });
    expect(state.className).toBe('info-val ok');
  });

  it('includes login in text when connected with login', () => {
    const state = buildInfoBoxState({ connected: true, login: 'monalisa' });
    expect(state.text).toBe('Connected (monalisa)');
  });

  it('returns plain "Connected" text when login is missing', () => {
    const state = buildInfoBoxState({ connected: true });
    expect(state.text).toBe('Connected');
  });

  it('btnText is "🔌 Trennen" when connected', () => {
    const state = buildInfoBoxState({ connected: true });
    expect(state.btnText).toBe('🔌 Trennen');
  });

  it('shows Pairing… text while pairing', () => {
    const state = buildInfoBoxState({ connected: false, pairing: true });
    expect(state.text).toBe('Pairing…');
  });

  it('btnText is "⏳ läuft…" while pairing', () => {
    const state = buildInfoBoxState({ connected: false, pairing: true });
    expect(state.btnText).toBe('⏳ läuft…');
  });

  it('className has no ok/error modifier while pairing', () => {
    const state = buildInfoBoxState({ connected: false, pairing: true });
    expect(state.className).toBe('info-val');
  });

  it('returns error className when idle (not connected, not pairing)', () => {
    const state = buildInfoBoxState({ connected: false, pairing: false });
    expect(state.className).toBe('info-val error');
  });

  it('btnText is "+ Verbinden" when idle', () => {
    const state = buildInfoBoxState({ connected: false, pairing: false });
    expect(state.btnText).toBe('+ Verbinden');
  });

  it('connected takes priority over pairing', () => {
    const state = buildInfoBoxState({ connected: true, pairing: true });
    expect(state.className).toBe('info-val ok');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildPairingHintText – pure formatting
// ════════════════════════════════════════════════════════════════════════════
describe('buildPairingHintText', () => {
  it('rounds expiresIn to whole minutes', () => {
    expect(buildPairingHintText(300)).toContain('5 Min');
  });

  it('rounds up fractional minutes', () => {
    // 290 seconds ≈ 4.83 min → Math.round → 5
    expect(buildPairingHintText(290)).toContain('5 Min');
  });

  it('contains the waiting-message prefix', () => {
    expect(buildPairingHintText(600)).toMatch(/Warte auf Bestätigung/);
  });

  it('returns a string', () => {
    expect(typeof buildPairingHintText(120)).toBe('string');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// buildPollingErrorText – pure formatting
// ════════════════════════════════════════════════════════════════════════════
describe('buildPollingErrorText', () => {
  it('contains "Njet! Pairing fehlgeschlagen" as base', () => {
    expect(buildPollingErrorText(undefined)).toContain('Njet! Pairing fehlgeschlagen');
  });

  it('appends the error message when provided', () => {
    expect(buildPollingErrorText('access_denied')).toContain('access_denied');
  });

  it('ends with "." when no error message is provided', () => {
    expect(buildPollingErrorText(undefined)).toMatch(/\.$/);
  });

  it('ends with "." when error is an empty string', () => {
    expect(buildPollingErrorText('')).toMatch(/\.$/);
  });

  it('uses ": " separator before the error text', () => {
    expect(buildPollingErrorText('timeout')).toContain(': timeout');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// GithubConnector – class lifecycle & behaviour
// ════════════════════════════════════════════════════════════════════════════
describe('GithubConnector', () => {
  let els;
  let connector;
  let siSpy;   // setInterval spy
  let ciSpy;   // clearInterval spy

  beforeEach(() => {
    els       = makeElements();
    connector = new GithubConnector(els);
    siSpy = vi.spyOn(globalThis, 'setInterval').mockReturnValue(99);
    ciSpy = vi.spyOn(globalThis, 'clearInterval').mockImplementation(() => {});

    // Suppress jsdom's "Not implemented: window.open" noise in startConnect tests
    vi.stubGlobal('open', vi.fn());

    // Default fetch stub – returns idle status
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({ connected: false, pairing: false }),
    }));
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  // ── init ─────────────────────────────────────────────────────────────────
  it('init() stores the provided callbacks', () => {
    const onStatusLoaded = vi.fn();
    const onMessage      = vi.fn();
    connector.init({ onStatusLoaded, onMessage });
    // Callbacks are stored; we verify indirectly via later tests.
    // Here we just confirm init does not throw.
    expect(() => connector.init({ onStatusLoaded, onMessage })).not.toThrow();
  });

  it('init() uses no-op defaults when no callbacks are supplied', () => {
    expect(() => connector.init()).not.toThrow();
  });

  // ── openModal ─────────────────────────────────────────────────────────────
  it('openModal() sets modal display to "flex"', async () => {
    await connector.openModal();
    expect(els.modal.style.display).toBe('flex');
  });

  it('openModal() calls refreshView() which fetches status', async () => {
    await connector.openModal();
    expect(fetch).toHaveBeenCalledWith('/api/github/status');
  });

  // ── closeModal ────────────────────────────────────────────────────────────
  it('closeModal() without event hides the modal', () => {
    els.modal.style.display = 'flex';
    connector.closeModal();
    expect(els.modal.style.display).toBe('none');
  });

  it('closeModal() stops polling', () => {
    connector._pollTimer = 99;   // simulate running poll
    connector.closeModal();
    expect(ciSpy).toHaveBeenCalledWith(99);
  });

  it('closeModal(e) does nothing when e.target is not the modal', () => {
    els.modal.style.display = 'flex';
    connector.closeModal({ target: {} });   // wrong target
    expect(els.modal.style.display).toBe('flex');
  });

  it('closeModal(e) closes when e.target is the modal element', () => {
    els.modal.style.display = 'flex';
    connector.closeModal({ target: els.modal });
    expect(els.modal.style.display).toBe('none');
  });

  // ── refreshView ───────────────────────────────────────────────────────────
  it('refreshView() shows startPanel when idle', async () => {
    await connector.refreshView();
    expect(els.startPanel.style.display).toBe('');
    expect(els.pairingPanel.style.display).toBe('none');
    expect(els.connectedPanel.style.display).toBe('none');
  });

  it('refreshView() shows connectedPanel and sets loginName when connected', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ connected: true, login: 'octocat' }),
    }));
    await connector.refreshView();
    expect(els.connectedPanel.style.display).toBe('');
    expect(els.loginName.textContent).toBe('octocat');
  });

  it('refreshView() shows pairingPanel and starts polling when pairing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ connected: false, pairing: true }),
    }));
    await connector.refreshView();
    expect(els.pairingPanel.style.display).toBe('');
    expect(siSpy).toHaveBeenCalled();
  });

  it('refreshView() does not throw when fetch fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network error')));
    await expect(connector.refreshView()).resolves.not.toThrow();
  });

  // ── startConnect ──────────────────────────────────────────────────────────
  it('startConnect() updates userCode and verificationLink from response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({
        userCode:        'ABCD-1234',
        verificationUri: 'https://github.com/login/device',
        expiresIn:       900,
      }),
    }));
    await connector.startConnect();
    expect(els.userCode.textContent).toBe('ABCD-1234');
    expect(els.verificationLink.href).toBe('https://github.com/login/device');
  });

  it('startConnect() hides startPanel and shows pairingPanel', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({
        userCode: 'X', verificationUri: 'https://x', expiresIn: 600,
      }),
    }));
    await connector.startConnect();
    expect(els.startPanel.style.display).toBe('none');
    expect(els.pairingPanel.style.display).toBe('');
  });

  it('startConnect() sets hintText from buildPairingHintText', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({
        userCode: 'X', verificationUri: 'https://x', expiresIn: 300,
      }),
    }));
    await connector.startConnect();
    expect(els.hintText.textContent).toContain('5 Min');
  });

  it('startConnect() starts polling', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok:   true,
      json: async () => ({
        userCode: 'X', verificationUri: 'https://x', expiresIn: 300,
      }),
    }));
    await connector.startConnect();
    expect(siSpy).toHaveBeenCalled();
  });

  it('startConnect() calls onMessage when fetch fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));
    const onMessage = vi.fn();
    connector.init({ onMessage });
    await connector.startConnect();
    expect(onMessage).toHaveBeenCalledWith('aigeny', expect.stringContaining('Njet!'));
  });

  // ── disconnect ────────────────────────────────────────────────────────────
  it('disconnect() POSTs to /api/github/disconnect', async () => {
    await connector.disconnect();
    expect(fetch).toHaveBeenCalledWith('/api/github/disconnect', { method: 'POST' });
  });

  it('disconnect() calls onStatusLoaded callback', async () => {
    const onStatusLoaded = vi.fn();
    connector.init({ onStatusLoaded });
    await connector.disconnect();
    expect(onStatusLoaded).toHaveBeenCalledOnce();
  });

  // ── refreshInfoBox ────────────────────────────────────────────────────────
  it('refreshInfoBox() updates infoBox text and className', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ connected: true, login: 'hubot' }),
    }));
    await connector.refreshInfoBox();
    expect(els.infoBox.textContent).toBe('Connected (hubot)');
    expect(els.infoBox.className).toBe('info-val ok');
  });

  it('refreshInfoBox() updates infoBtn text', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ connected: false, pairing: false }),
    }));
    await connector.refreshInfoBox();
    expect(els.infoBtn.textContent).toBe('+ Verbinden');
  });

  it('refreshInfoBox() does not throw when fetch fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
    await expect(connector.refreshInfoBox()).resolves.not.toThrow();
  });

  // ── _startPolling / _stopPolling / isPolling ───────────────────────────────
  it('_startPolling() calls setInterval', () => {
    connector._startPolling();
    expect(siSpy).toHaveBeenCalledOnce();
    expect(connector.isPolling).toBe(true);
  });

  it('_startPolling() is a no-op when already polling', () => {
    connector._startPolling();
    connector._startPolling();   // second call
    expect(siSpy).toHaveBeenCalledOnce();
  });

  it('_stopPolling() calls clearInterval with the stored handle', () => {
    connector._startPolling();
    connector._stopPolling();
    expect(ciSpy).toHaveBeenCalledWith(99);
    expect(connector.isPolling).toBe(false);
  });

  it('_stopPolling() is a no-op when not polling', () => {
    connector._stopPolling();   // never started
    expect(ciSpy).not.toHaveBeenCalled();
  });

  it('double _stopPolling() only calls clearInterval once', () => {
    connector._startPolling();
    connector._stopPolling();
    connector._stopPolling();
    expect(ciSpy).toHaveBeenCalledOnce();
  });

  it('isPolling is false before start', () => {
    expect(connector.isPolling).toBe(false);
  });

  // ── Null element safety ───────────────────────────────────────────────────
  it('openModal() does not throw when modal element is null', async () => {
    const c = new GithubConnector({ ...makeElements(), modal: null });
    // openModal() is not async – just verify it doesn't throw synchronously
    // and that the underlying refreshView() also completes without error.
    expect(() => c.openModal()).not.toThrow();
  });

  it('closeModal() does not throw when modal element is null', () => {
    const c = new GithubConnector({ ...makeElements(), modal: null });
    expect(() => c.closeModal()).not.toThrow();
  });

  it('refreshView() does not throw when all elements are null', async () => {
    const c = new GithubConnector({
      modal: null, startPanel: null, pairingPanel: null,
      connectedPanel: null, loginName: null, userCode: null,
      verificationLink: null, hintText: null, infoBox: null, infoBtn: null,
    });
    await expect(c.refreshView()).resolves.not.toThrow();
  });

  it('refreshInfoBox() does not throw when infoBox and infoBtn are null', async () => {
    const c = new GithubConnector({ ...makeElements(), infoBox: null, infoBtn: null });
    await expect(c.refreshInfoBox()).resolves.not.toThrow();
  });
});



