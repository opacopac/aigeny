/**
 * github-connect.js – GitHub Copilot OAuth Device-Flow Connector
 *
 * Three exported layers, deliberately separated by testability
 * (same pattern as hal-eye.js):
 *
 *  buildConnectViewState(data)
 *    Pure function – maps /api/github/status response to a panel-visibility
 *    descriptor { panel, loginName }.  No DOM access, fully unit-testable.
 *
 *  buildInfoBoxState(data)
 *    Pure function – maps /api/github/status response to the sidebar info-box
 *    display values { text, className, btnText }.  No DOM access.
 *
 *  buildPairingHintText(expiresIn)
 *    Pure function – formats the countdown hint string from the expiresIn
 *    value returned by /api/github/connect.
 *
 *  buildPollingErrorText(lastError)
 *    Pure function – formats the error message shown when pairing ends
 *    without a successful connection.
 *
 *  GithubConnector
 *    Stateful class that owns the polling timer, issues fetch calls, and
 *    delegates all UI writes to the pure functions above.
 *    Consumers inject onStatusLoaded and onMessage callbacks so the class
 *    never reads global state directly.
 */

// ── Pure state-mapping functions ─────────────────────────────────────────────

/**
 * Maps a /api/github/status response object to a panel-visibility descriptor.
 *
 * @param {{ connected: boolean, pairing: boolean, login?: string }} data
 * @returns {{ panel: 'connected'|'pairing'|'start', loginName?: string }}
 */
export function buildConnectViewState(data) {
  if (data.connected) {
    return { panel: 'connected', loginName: data.login || '(unbekannt)' };
  }
  if (data.pairing) {
    return { panel: 'pairing' };
  }
  return { panel: 'start' };
}

/**
 * Maps a /api/github/status response object to info-box display values.
 *
 * @param {{ connected: boolean, pairing: boolean, login?: string }} data
 * @returns {{ text: string, className: string, btnText: string }}
 */
export function buildInfoBoxState(data) {
  if (data.connected) {
    return {
      text:      data.login ? `Connected (${data.login})` : 'Connected',
      className: 'info-val ok',
      btnText:   '🔌 Trennen',
    };
  }
  if (data.pairing) {
    return {
      text:      'Pairing…',
      className: 'info-val',
      btnText:   '⏳ läuft…',
    };
  }
  return {
    text:      'Nicht verbunden',
    className: 'info-val error',
    btnText:   '+ Verbinden',
  };
}

/**
 * Formats the hint text shown while waiting for the user to enter the code.
 *
 * @param {number} expiresIn – seconds until the device code expires
 * @returns {string}
 */
export function buildPairingHintText(expiresIn) {
  return `Warte auf Bestätigung… (Code läuft in ${Math.round(expiresIn / 60)} Min ab)`;
}

/**
 * Formats the error text shown when device-flow pairing ends without success.
 *
 * @param {string|undefined} lastError
 * @returns {string}
 */
export function buildPollingErrorText(lastError) {
  return 'Njet! Pairing fehlgeschlagen' + (lastError ? ': ' + lastError : '.');
}

// ── Stateful connector class ──────────────────────────────────────────────────

export class GithubConnector {
  /**
   * @param {{
   *   modal:           HTMLElement|null,
   *   startPanel:      HTMLElement|null,
   *   pairingPanel:    HTMLElement|null,
   *   connectedPanel:  HTMLElement|null,
   *   loginName:       HTMLElement|null,
   *   userCode:        HTMLElement|null,
   *   verificationLink:HTMLAnchorElement|null,
   *   hintText:        HTMLElement|null,
   *   infoBox:         HTMLElement|null,
   *   infoBtn:         HTMLElement|null,
   * }} elements – references to the DOM nodes owned by this connector
   */
  constructor(elements) {
    this._els        = elements;
    this._pollTimer  = null;
    this._onStatusLoaded = () => {};
    this._onMessage      = () => {};
  }

  /**
   * Inject app-level callbacks.  Call once after construction.
   *
   * @param {{ onStatusLoaded?: () => void, onMessage?: (role:string, text:string) => void }} callbacks
   */
  init({ onStatusLoaded = () => {}, onMessage = () => {} } = {}) {
    this._onStatusLoaded = onStatusLoaded;
    this._onMessage      = onMessage;
  }

  // ── Modal lifecycle ────────────────────────────────────────────────────────

  openModal() {
    if (this._els.modal) this._els.modal.style.display = 'flex';
    this.refreshView();
  }

  /**
   * Close the modal.  When called from an onclick backdrop handler the event
   * target must be the modal overlay itself (same guard as the original code).
   * @param {Event} [e]
   */
  closeModal(e) {
    if (e && e.target !== this._els.modal) return;
    if (this._els.modal) this._els.modal.style.display = 'none';
    this._stopPolling();
  }

  // ── View refresh ───────────────────────────────────────────────────────────

  /** Fetch current status and update the connect-modal panels. */
  async refreshView() {
    try {
      const res  = await fetch('/api/github/status');
      const data = await res.json();
      const state = buildConnectViewState(data);
      this._applyConnectViewState(state);
      if (state.panel === 'pairing') this._startPolling();
    } catch (e) {
      console.error('GitHub status failed', e);
    }
  }

  /** @private – write a buildConnectViewState result to the DOM elements. */
  _applyConnectViewState(state) {
    const { startPanel, pairingPanel, connectedPanel, loginName } = this._els;
    if (startPanel)      startPanel.style.display      = state.panel === 'start'     ? '' : 'none';
    if (pairingPanel)    pairingPanel.style.display    = state.panel === 'pairing'   ? '' : 'none';
    if (connectedPanel)  connectedPanel.style.display  = state.panel === 'connected' ? '' : 'none';
    if (loginName && state.loginName) loginName.textContent = state.loginName;
  }

  /** Fetch current status and update the sidebar info-box. */
  async refreshInfoBox() {
    try {
      const res  = await fetch('/api/github/status');
      const data = await res.json();
      const state = buildInfoBoxState(data);
      if (this._els.infoBox) {
        this._els.infoBox.textContent = state.text;
        this._els.infoBox.className   = state.className;
      }
      if (this._els.infoBtn) this._els.infoBtn.textContent = state.btnText;
    } catch { /* ignore */ }
  }

  // ── User actions ───────────────────────────────────────────────────────────

  /** POST /api/github/connect → start the device-flow pairing. */
  async startConnect() {
    try {
      const res = await fetch('/api/github/connect', { method: 'POST' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();

      if (this._els.userCode)          this._els.userCode.textContent      = data.userCode;
      if (this._els.verificationLink) {
        this._els.verificationLink.href        = data.verificationUri;
        this._els.verificationLink.textContent = data.verificationUri;
      }
      if (this._els.startPanel)   this._els.startPanel.style.display  = 'none';
      if (this._els.pairingPanel) this._els.pairingPanel.style.display = '';
      if (this._els.hintText)     this._els.hintText.textContent = buildPairingHintText(data.expiresIn);

      // Open the verification URL in a new tab automatically
      try { window.open(data.verificationUri, '_blank', 'noopener'); } catch { /* ignore */ }

      this._startPolling();
    } catch (err) {
      this._onMessage('aigeny',
        'Njet! GitHub Verbindung konnte nicht gestartet werden: ' + err.message);
    }
  }

  /** Copy the displayed user code to the clipboard. */
  copyUserCode() {
    const code = this._els.userCode?.textContent ?? '';
    navigator.clipboard.writeText(code).catch(() => {});
  }

  /** POST /api/github/disconnect → reset to idle state. */
  async disconnect() {
    await fetch('/api/github/disconnect', { method: 'POST' });
    this.refreshView();
    this._onStatusLoaded();
  }

  // ── Polling ────────────────────────────────────────────────────────────────

  _startPolling() {
    if (this._pollTimer) return;
    this._pollTimer = setInterval(async () => {
      try {
        const res  = await fetch('/api/github/status');
        const data = await res.json();
        if (data.connected) {
          this._stopPolling();
          if (this._els.pairingPanel)  this._els.pairingPanel.style.display  = 'none';
          if (this._els.connectedPanel) this._els.connectedPanel.style.display = '';
          if (this._els.loginName)     this._els.loginName.textContent = data.login || '(unbekannt)';
          this._onStatusLoaded();
          this._onMessage('aigeny',
            'Da, GitHub-Verbindung steht, Towarischtsch! Verfügbare Modelle stehen im Log, choroscho!');
        } else if (!data.pairing) {
          // Pairing ended without success (timeout/error)
          this._stopPolling();
          if (this._els.hintText) this._els.hintText.textContent = buildPollingErrorText(data.lastError);
        }
      } catch { /* keep trying */ }
    }, 2500);
  }

  _stopPolling() {
    if (this._pollTimer !== null) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  }

  /** @returns {boolean} true while a polling interval is active */
  get isPolling() {
    return this._pollTimer !== null;
  }
}


