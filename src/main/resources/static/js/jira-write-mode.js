/**
 * write-mode.js – Jira Write-Mode Toggle
 *
 * Same three-layer pattern as hal-eye.js and token-modal.js:
 *
 *  buildWriteToggleState(enabled)
 *    Pure function – no DOM access, fully unit-testable.
 *    Returns the complete UI state derived from the enabled flag.
 *
 *  JiraWriteMode
 *    Stateful class – wraps fetch, localStorage and DOM updates.
 *    Accepts storageKey and apiEndpoint for testability.
 *    Elements ({ toggle, label }) are injected at construction time.
 */

// ── Pure state function ───────────────────────────────────────────────────────

/**
 * Derive the complete UI state for the write-mode toggle from a boolean.
 *
 * @param {boolean} enabled
 * @returns {{ checked: boolean, labelText: string, labelColor: string }}
 */
export function buildWriteToggleState(enabled) {
  return {
    checked:    enabled,
    labelText:  '✏ Jira Schreiben (' + (enabled ? 'ein' : 'aus') + ')',
    labelColor: enabled ? 'var(--red)' : '',
  };
}

// ── Stateful class ────────────────────────────────────────────────────────────

export class JiraWriteMode {
  /**
   * @param {string} storageKey   – localStorage key (e.g. 'aigeny.jiraWriteEnabled')
   * @param {string} apiEndpoint  – backend route   (e.g. '/api/jira/write-mode')
   * @param {{
   *   toggle: HTMLInputElement|null,
   *   label:  HTMLElement|null,
   * }} elements
   */
  constructor(storageKey, apiEndpoint, elements) {
    this._storageKey  = storageKey;
    this._apiEndpoint = apiEndpoint;
    this._els         = elements;
    this._onToggled   = () => {};
  }

  /**
   * Inject app-level callbacks.  Call once after construction.
   * @param {{ onToggled?: (enabled: boolean) => void }} callbacks
   */
  init({ onToggled = () => {} } = {}) {
    this._onToggled = onToggled;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Toggle the write mode on or off.
   * POSTs the new state to the backend, then updates localStorage and the DOM.
   * Errors are logged but not re-thrown so a network failure cannot break the UI.
   *
   * @param {boolean} enabled
   */
  async toggle(enabled) {
    try {
      await fetch(this._apiEndpoint, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ enabled }),
      });
      localStorage.setItem(this._storageKey, enabled ? 'true' : 'false');
      this._applyState(buildWriteToggleState(enabled));
      this._onToggled(enabled);
    } catch (err) {
      console.error('Failed to set write mode:', err);
    }
  }

  /**
   * Reset write mode to disabled on page load.
   * Always disables for safety, regardless of any previously stored value.
   */
  async syncToSession() {
    localStorage.setItem(this._storageKey, 'false');
    this._applyState(buildWriteToggleState(false));
    try {
      await fetch(this._apiEndpoint, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ enabled: false }),
      });
    } catch { /* ignore */ }
  }

  // ── Private helper ────────────────────────────────────────────────────────

  /** @private */
  _applyState({ checked, labelText, labelColor }) {
    if (this._els.toggle) this._els.toggle.checked         = checked;
    if (this._els.label)  this._els.label.textContent      = labelText;
    if (this._els.label)  this._els.label.style.color      = labelColor;
  }
}

