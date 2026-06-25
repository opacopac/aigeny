/**
 * status-panel.js – Sidebar Status Panel
 *
 * Same three-layer pattern as hal-eye.js / token-modal.js / jira-write-mode.js:
 *
 *  buildDbState(data)            – pure, no DOM
 *  buildJiraState(data)          – pure, no DOM
 *  buildBitbucketState(data)     – pure, no DOM
 *  buildStatusIndicatorState     – pure, no DOM
 *  buildThinkingState(thinking)  – pure, no DOM
 *
 *  StatusPanel                   – stateful class, owns all DOM updates for
 *                                  the sidebar info-box and the header status dot.
 */

// ── Pure state functions ──────────────────────────────────────────────────────

/**
 * @param {{ dbConfigured: boolean, dbUsername?: string }} data
 * @returns {{ text: string, className: string }}
 */
export function buildDbState(data) {
  if (data.dbConfigured) {
    const user = data.dbUsername ? ` (${data.dbUsername})` : '';
    return { text: 'Connected' + user, className: 'info-val ok' };
  }
  return { text: 'Not configured', className: 'info-val error' };
}

/**
 * @param {{
 *   jiraBaseUrlConfigured: boolean,
 *   jiraConfigured: boolean,
 * }} data
 * @returns {{
 *   text: string, className: string,
 *   btnText: string, btnVisible: boolean,
 *   toggleVisible: boolean,
 * }}
 */
export function buildJiraState(data) {
  if (data.jiraBaseUrlConfigured) {
    if (data.jiraConfigured) {
      return {
        text: 'Connected',    className: 'info-val ok',
        btnText: '✎ Token ändern',  btnVisible: true,
        toggleVisible: true,
      };
    }
    return {
      text: 'Kein Token',   className: 'info-val error',
      btnText: '+ Token eingeben', btnVisible: true,
      toggleVisible: true,
    };
  }
  return {
    text: 'Not configured', className: 'info-val error',
    btnText: '',            btnVisible: false,
    toggleVisible: false,
  };
}

/**
 * @param {{
 *   bitbucketBaseUrlConfigured: boolean,
 *   bitbucketConfigured: boolean,
 * }} data
 * @returns {{ text: string, className: string, btnText: string, btnVisible: boolean }}
 */
export function buildBitbucketState(data) {
  if (data.bitbucketBaseUrlConfigured) {
    if (data.bitbucketConfigured) {
      return { text: 'Connected',  className: 'info-val ok',    btnText: '✎ Token ändern',  btnVisible: true };
    }
    return   { text: 'Kein Token', className: 'info-val error', btnText: '+ Token eingeben', btnVisible: true };
  }
  return { text: 'Not configured', className: 'info-val error', btnText: '', btnVisible: false };
}

/**
 * @param {boolean} thinking
 * @param {boolean} hasError  – true when the /api/status fetch failed
 * @returns {{ type: string, text: string }}
 */
export function buildStatusIndicatorState(thinking, hasError) {
  if (thinking) return { type: 'busy', text: 'Denkt nach...' };
  if (hasError)  return { type: 'error', text: 'Server nicht erreichbar' };
  return           { type: 'ok',    text: 'Ready' };
}

/**
 * @param {boolean} thinking
 * @returns {{
 *   sendDisabled: boolean, sendVisible: boolean,
 *   stopVisible: boolean, statusText: string,
 * }}
 */
export function buildThinkingState(thinking) {
  return {
    sendDisabled: thinking,
    sendVisible:  !thinking,
    stopVisible:  thinking,
    statusText:   thinking ? 'AIgeny mysleet... (denkt nach)' : 'Bereit, Towarischtsch.',
  };
}

// ── Stateful class ────────────────────────────────────────────────────────────

export class StatusPanel {
  /**
   * @param {{
   *   infoLlm:        HTMLElement|null,
   *   infoModel:      HTMLElement|null,
   *   infoTables:     HTMLElement|null,
   *   infoDb:         HTMLElement|null,
   *   infoJira:       HTMLElement|null,
   *   btnJiraToken:   HTMLElement|null,
   *   jiraWriteRow:   HTMLElement|null,
   *   infoBitbucket:  HTMLElement|null,
   *   btnBitbucket:   HTMLElement|null,
   *   githubInfoRow:  HTMLElement|null,
   *   statusDot:      HTMLElement|null,
   *   statusText:     HTMLElement|null,
   *   sendBtn:        HTMLElement|null,
   *   stopBtn:        HTMLElement|null,
   *   halStatusText:  HTMLElement|null,
   *   btnCsv:         HTMLElement|null,
   * }} elements
   */
  constructor(elements) {
    this._els = elements;
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /**
   * Apply a full /api/status response to the sidebar.
   * @param {object} data – parsed JSON from /api/status
   */
  applyStatus(data) {
    const els = this._els;

    if (els.infoLlm)    els.infoLlm.textContent    = data.llmProvider  || '—';
    if (els.infoModel)  els.infoModel.textContent   = data.llmModel     || '—';
    if (els.infoTables) els.infoTables.textContent  = data.schemaTables || '0';

    // GitHub row – only shown when provider is github-copilot
    if (els.githubInfoRow) {
      els.githubInfoRow.style.display = (data.llmProvider === 'github-copilot') ? '' : 'none';
    }

    // DB
    this._applyInfoRow(els.infoDb, null, buildDbState(data));

    // Jira
    const jira = buildJiraState(data);
    this._applyInfoRow(els.infoJira, els.btnJiraToken, jira);
    if (els.jiraWriteRow) els.jiraWriteRow.style.display = jira.toggleVisible ? 'flex' : 'none';

    // Bitbucket
    const bb = buildBitbucketState(data);
    this._applyInfoRow(els.infoBitbucket, els.btnBitbucket, bb);
  }

  /**
   * Update the header status indicator.
   * @param {string} type – 'ok' | 'busy' | 'error'
   * @param {string} text
   */
  setStatusIndicator(type, text) {
    if (this._els.statusDot)  this._els.statusDot.className   = 'status-dot ' + type;
    if (this._els.statusText) this._els.statusText.textContent = text;
  }

  /**
   * Reflect the thinking state in the toolbar and HAL status text.
   * @param {boolean} thinking
   */
  setThinking(thinking) {
    const s = buildThinkingState(thinking);
    const els = this._els;
    if (els.sendBtn) {
      els.sendBtn.disabled     = s.sendDisabled;
      els.sendBtn.style.display = s.sendVisible ? '' : 'none';
    }
    if (els.stopBtn) els.stopBtn.style.display = s.stopVisible ? '' : 'none';
    if (els.halStatusText) els.halStatusText.textContent = s.statusText;
    this.setStatusIndicator(
      thinking ? 'busy' : 'ok',
      thinking ? 'Denkt nach...' : 'Bereit'
    );
  }

  /**
   * Enable or disable the CSV export button.
   * @param {boolean} enabled
   */
  setExportEnabled(enabled) {
    if (this._els.btnCsv) this._els.btnCsv.disabled = !enabled;
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  /** @private */
  _applyInfoRow(valEl, btnEl, { text, className, btnText = '', btnVisible = false }) {
    if (valEl) {
      valEl.textContent = text;
      valEl.className   = className;
    }
    if (btnEl) {
      if (btnText) btnEl.textContent = btnText;
      btnEl.classList.toggle('visible', btnVisible);
    }
  }
}

