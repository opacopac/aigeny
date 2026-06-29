/**
 * schema-panel.js – DB-Schema reload button panel
 *
 * Follows the same three-layer pattern used throughout the project:
 *
 *  buildSchemaButtonState(loading)  – pure, no DOM, fully deterministic.
 *  SchemaPanel                      – stateful class, owns all DOM updates
 *                                     for the reload button and infoTables span.
 *
 * Extracted from app.js to satisfy the Single-Responsibility Principle (S-5):
 * the previous inline reloadSchema() combined fetch logic, button-state management
 * and direct appendMessage calls in a single place.
 */

// ── Pure state function ───────────────────────────────────────────────────────

/**
 * Returns the desired button state for the given loading flag.
 *
 * @param {boolean} loading – true while the fetch is in progress
 * @returns {{ disabled: boolean, text: string }}
 */
export function buildSchemaButtonState(loading) {
  if (loading) return { disabled: true,  text: '⌛ Lade...' };
  return         { disabled: false, text: '↻ Schema neu laden' };
}

// ── Stateful class ────────────────────────────────────────────────────────────

export class SchemaPanel {
  /**
   * @param {{
   *   btn:        HTMLElement|null,
   *   infoTables: HTMLElement|null,
   * }} elements
   * @param {string} [apiEndpoint] – defaults to '/api/schema/reload'
   */
  constructor(elements, apiEndpoint = '/api/schema/reload') {
    this._els     = elements;
    this._api     = apiEndpoint;
  }

  /**
   * Reload the DB schema via the backend API.
   *
   * @param {function(string, string): void} appendMessageFn
   *   Callback to post a chat message, called as appendMessageFn(role, text).
   */
  async reload(appendMessageFn) {
    this._applyButtonState(buildSchemaButtonState(true));
    try {
      const res  = await fetch(this._api, { method: 'POST' });
      const data = await res.json();
      if (data.status === 'ok') {
        if (this._els.infoTables) this._els.infoTables.textContent = data.tables;
        appendMessageFn('aigeny',
          `Da! Schema neu geladen, Towarischtsch. Ich kenne jetzt **${data.tables}** Tabellen in Datenbank. Otschen choroscho!`);
      } else {
        appendMessageFn('aigeny', 'Njet! Schema-Neuladen fehlgeschlagen: ' + (data.message || 'unbekannter Fehler'));
      }
    } catch (err) {
      appendMessageFn('aigeny', 'Njet! Schema konnte nicht geladen werden: ' + err.message);
    } finally {
      this._applyButtonState(buildSchemaButtonState(false));
    }
  }

  // ── Private helpers ─────────────────────────────────────────────────────────

  /** @private */
  _applyButtonState({ disabled, text }) {
    if (!this._els.btn) return;
    this._els.btn.disabled    = disabled;
    this._els.btn.textContent = text;
  }
}

