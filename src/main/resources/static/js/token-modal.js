/**
 * token-modal.js – Generic API-Token Modal
 *
 * Eliminates the duplication between the Jira and Bitbucket token modals.
 * Both share an identical open / close / save / clear / syncToSession
 * lifecycle that differs only in three configuration values:
 *   storageKey   – localStorage key (e.g. 'aigeny.jiraToken')
 *   apiEndpoint  – backend route  (e.g. '/api/jira/token')
 *   elements     – { modal, input, hint } DOM references
 *
 * Same three-layer pattern as hal-eye.js and github-connect.js:
 *
 *  buildHintOnOpen(storedToken)      – pure, no DOM
 *  buildHintOnSaveSuccess(token)     – pure, no DOM
 *  buildHintOnClear()                – pure, no DOM
 *  buildHintOnError(message)         – pure, no DOM
 *
 *  TokenModal                        – stateful class, DOM + fetch
 */

// ── Delays (ms) ──────────────────────────────────────────────────────────────
export const CLOSE_DELAY_SAVE  = 900;
export const CLOSE_DELAY_CLEAR = 600;

// ── Pure hint-state functions ─────────────────────────────────────────────────

/**
 * Hint state to show when the modal opens.
 * @param {string} storedToken – value currently in localStorage ('' if none)
 * @returns {{ text: string, className: string }}
 */
export function buildHintOnOpen(storedToken) {
  return storedToken
    ? { text: '✔ Token ist gespeichert.', className: 'modal-hint ok' }
    : { text: '',                          className: 'modal-hint'    };
}

/**
 * Hint state after a successful save POST.
 * @param {string} token – the token that was saved (empty string = deleted)
 * @returns {{ text: string, className: string }}
 */
export function buildHintOnSaveSuccess(token) {
  return token
    ? { text: '✔ Token gespeichert! Da, choroscho!', className: 'modal-hint ok' }
    : { text: 'Token gelöscht.',                      className: 'modal-hint'    };
}

/**
 * Hint state after clearing the token via the clear button.
 * @returns {{ text: string, className: string }}
 */
export function buildHintOnClear() {
  return { text: 'Token gelöscht.', className: 'modal-hint' };
}

/**
 * Hint state when the save POST fails.
 * @param {string} message – error message from the caught exception
 * @returns {{ text: string, className: string }}
 */
export function buildHintOnError(message) {
  return { text: 'Njet! Fehler: ' + message, className: 'modal-hint error' };
}

// ── Stateful modal class ──────────────────────────────────────────────────────

export class TokenModal {
  /**
   * @param {string} storageKey  – localStorage key for the token
   * @param {string} apiEndpoint – backend route to POST the token to
   * @param {{
   *   modal: HTMLElement|null,
   *   input: HTMLInputElement|null,
   *   hint:  HTMLElement|null,
   * }} elements
   */
  constructor(storageKey, apiEndpoint, elements) {
    this._storageKey  = storageKey;
    this._apiEndpoint = apiEndpoint;
    this._els         = elements;
    this._onSaved     = () => {};
  }

  /**
   * Inject app-level callbacks.  Call once after construction.
   * @param {{ onSaved?: () => void }} callbacks
   */
  init({ onSaved = () => {} } = {}) {
    this._onSaved = onSaved;
  }

  // ── Modal lifecycle ────────────────────────────────────────────────────────

  /** Open the modal, pre-filling the input from localStorage. */
  open() {
    const stored = localStorage.getItem(this._storageKey) || '';
    if (this._els.input) this._els.input.value = stored;
    this._applyHint(buildHintOnOpen(stored));
    if (this._els.modal) this._els.modal.style.display = 'flex';
    setTimeout(() => this._els.input?.focus(), 50);
  }

  /**
   * Close the modal.  When called from an onclick backdrop handler the event
   * target must be the modal overlay itself.
   * @param {Event} [e]
   */
  close(e) {
    if (e && e.target !== this._els.modal) return;
    if (this._els.modal) this._els.modal.style.display = 'none';
  }

  // ── Token operations ───────────────────────────────────────────────────────

  /** Read token from input, POST to backend, update localStorage & hint. */
  async save() {
    const token = this._els.input?.value.trim() ?? '';
    try {
      const res = await fetch(this._apiEndpoint, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ token }),
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      if (token) {
        localStorage.setItem(this._storageKey, token);
      } else {
        localStorage.removeItem(this._storageKey);
      }
      this._applyHint(buildHintOnSaveSuccess(token));
      setTimeout(() => {
        if (this._els.modal) this._els.modal.style.display = 'none';
        this._onSaved();
      }, CLOSE_DELAY_SAVE);
    } catch (err) {
      this._applyHint(buildHintOnError(err.message));
    }
  }

  /** Remove token from localStorage, POST empty value, reset input & hint. */
  async clear() {
    localStorage.removeItem(this._storageKey);
    try {
      await fetch(this._apiEndpoint, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ token: '' }),
      });
    } catch { /* ignore */ }
    if (this._els.input) this._els.input.value = '';
    this._applyHint(buildHintOnClear());
    setTimeout(() => {
      if (this._els.modal) this._els.modal.style.display = 'none';
      this._onSaved();
    }, CLOSE_DELAY_CLEAR);
  }

  /**
   * Re-send the token stored in localStorage to the backend session.
   * Called on page load to restore the session after a browser refresh.
   */
  async syncToSession() {
    const token = localStorage.getItem(this._storageKey);
    if (!token) return;
    try {
      await fetch(this._apiEndpoint, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ token }),
      });
    } catch { /* ignore */ }
  }

  // ── Private helper ─────────────────────────────────────────────────────────

  /** @private */
  _applyHint({ text, className }) {
    if (this._els.hint) {
      this._els.hint.textContent = text;
      this._els.hint.className   = className;
    }
  }
}

