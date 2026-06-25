/* ─────────────────────────────────────────────────────────────────────────
   AIgeny - frontend JavaScript
   - Chat send / receive
   - Export buttons
   - Status polling
   ───────────────────────────────────────────────────────────────────────── */

import { HalEyeAnimator } from './hal-eye.js';
import { initChat, appendMessage } from './chat.js';
import { GithubConnector } from './github-connect.js';
import { TokenModal } from './token-modal.js';

// ── State ──────────────────────────────────────────────────────────────────
let isThinking = false;
let hasExportData = false;
const JIRA_WRITE_KEY = 'aigeny.jiraWriteEnabled';

// ── HAL Eye ────────────────────────────────────────────────────────────────
// Canvas elements are available because ES-module scripts are deferred by default.
const _halAnimator = new HalEyeAnimator(
  document.getElementById('halEye'),
  document.getElementById('halEyeMini')
);
_halAnimator.start(() => isThinking);


// ── Jira Write Mode ────────────────────────────────────────────────────────

async function toggleJiraWriteMode(enabled) {
  try {
    await fetch('/api/jira/write-mode', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled })
    });
    localStorage.setItem(JIRA_WRITE_KEY, enabled ? 'true' : 'false');
    updateWriteToggleUI(enabled);
  } catch (err) {
    console.error('Failed to set Jira write mode:', err);
  }
}

function updateWriteToggleUI(enabled) {
  const toggle = document.getElementById('jiraWriteToggle');
  if (toggle) toggle.checked = enabled;
  const label = document.getElementById('jiraWriteLabel');
  if (label) {
    label.style.color = enabled ? 'var(--red)' : '';
    label.textContent = '✏ Jira Schreiben (' + (enabled ? 'ein' : 'aus') + ')';
  }
}

async function syncJiraWriteModeToSession() {
  // Always start with write mode disabled on page load for safety
  localStorage.setItem(JIRA_WRITE_KEY, 'false');
  updateWriteToggleUI(false);
  try {
    await fetch('/api/jira/write-mode', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ enabled: false })
    });
  } catch (e) { /* ignore */ }
}

// ── Token Modals ───────────────────────────────────────────────────────────

const _jiraTokenModal = new TokenModal(
  'aigeny.jiraToken',
  '/api/jira/token',
  {
    modal: document.getElementById('jiraTokenModal'),
    input: document.getElementById('jiraTokenInput'),
    hint:  document.getElementById('jiraTokenHint'),
  }
);

const _bitbucketTokenModal = new TokenModal(
  'aigeny.bitbucketToken',
  '/api/bitbucket/token',
  {
    modal: document.getElementById('bitbucketTokenModal'),
    input: document.getElementById('bitbucketTokenInput'),
    hint:  document.getElementById('bitbucketTokenHint'),
  }
);

function openJiraTokenModal()           { _jiraTokenModal.open(); }
function closeJiraTokenModal(e)         { _jiraTokenModal.close(e); }
function saveJiraToken()                { _jiraTokenModal.save(); }
function clearJiraToken()               { _jiraTokenModal.clear(); }

function openBitbucketTokenModal()      { _bitbucketTokenModal.open(); }
function closeBitbucketTokenModal(e)    { _bitbucketTokenModal.close(e); }
function saveBitbucketToken()           { _bitbucketTokenModal.save(); }
function clearBitbucketToken()          { _bitbucketTokenModal.clear(); }

// Close modal on Escape
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    _jiraTokenModal.close();
    _bitbucketTokenModal.close();
    const gh = document.getElementById('githubConnectModal');
    if (gh) gh.style.display = 'none';
  }
});


// ── GitHub Copilot Connect (OAuth Device Flow) ─────────────────────────────

const _githubConnector = new GithubConnector({
  modal:            document.getElementById('githubConnectModal'),
  startPanel:       document.getElementById('githubConnectStart'),
  pairingPanel:     document.getElementById('githubConnectPairing'),
  connectedPanel:   document.getElementById('githubConnectConnected'),
  loginName:        document.getElementById('githubLoginName'),
  userCode:         document.getElementById('githubUserCode'),
  verificationLink: document.getElementById('githubVerificationLink'),
  hintText:         document.getElementById('githubConnectHint'),
  infoBox:          document.getElementById('infoGithub'),
  infoBtn:          document.getElementById('btnGithubConnect'),
});

function openGithubConnectModal()       { _githubConnector.openModal(); }
function closeGithubConnectModal(e)     { _githubConnector.closeModal(e); }
function startGithubConnect()           { _githubConnector.startConnect(); }
function copyGithubUserCode()           { _githubConnector.copyUserCode(); }
function disconnectGithub()             { _githubConnector.disconnect(); }
async function refreshGithubInfoBox()   { await _githubConnector.refreshInfoBox(); }


// ── Controls ───────────────────────────────────────────────────────────────


async function reloadSchema() {
  const btn = event.target;
  btn.disabled = true;
    btn.textContent = '⌛ Lade...';
  try {
    const res = await fetch('/api/schema/reload', { method: 'POST' });
    const data = await res.json();
    if (data.status === 'ok') {
      document.getElementById('infoTables').textContent = data.tables;
      appendMessage('aigeny',
        `Da! Schema neu geladen, Towarischtsch. Ich kenne jetzt **${data.tables}** Tabellen in Datenbank. Otschen choroscho!`);
    } else {
      appendMessage('aigeny', 'Njet! Schema-Neuladen fehlgeschlagen: ' + (data.message || 'unbekannter Fehler'));
    }
  } catch (err) {
    appendMessage('aigeny', 'Njet! Schema konnte nicht geladen werden: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.textContent = '↻ Schema neu laden';
  }
}


// ── State helpers ──────────────────────────────────────────────────────────

function setThinking(thinking) {
  isThinking = thinking;
  document.getElementById('sendBtn').disabled = thinking;
  document.getElementById('sendBtn').style.display = thinking ? 'none' : '';
  document.getElementById('stopBtn').style.display = thinking ? '' : 'none';
  document.getElementById('halStatusText').textContent =
    thinking ? 'AIgeny mysleet... (denkt nach)' : 'Bereit, Towarischtsch.';
  setStatusIndicator(thinking ? 'busy' : 'ok', thinking ? 'Denkt nach...' : 'Bereit');
}

function setExportButtons(enabled) {
  document.getElementById('btnCsv').disabled = !enabled;
}

function setStatusIndicator(type, text) {
  const dot  = document.getElementById('statusDot');
  const span = document.getElementById('statusText');
  dot.className  = 'status-dot ' + type;
  span.textContent = text;
}

// ── Status polling ─────────────────────────────────────────────────────────

async function loadStatus() {
  try {
    const res  = await fetch('/api/status');
    const data = await res.json();

    document.getElementById('infoLlm').textContent   = data.llmProvider  || '—';
    document.getElementById('infoModel').textContent = data.llmModel     || '—';
    document.getElementById('infoTables').textContent= data.schemaTables || '0';

    const githubRow = document.getElementById('githubInfoRow');
    if (githubRow) {
      githubRow.style.display = (data.llmProvider === 'github-copilot') ? '' : 'none';
    }

    const dbEl = document.getElementById('infoDb');
    if (data.dbConfigured) {
      const user = data.dbUsername ? ` (${data.dbUsername})` : '';
      dbEl.textContent = 'Connected' + user;
      dbEl.className   = 'info-val ok';
    } else {
      dbEl.textContent = 'Not configured';
      dbEl.className   = 'info-val error';
    }

    const jiraEl  = document.getElementById('infoJira');
    const jiraBtn = document.getElementById('btnJiraToken');
    const jiraWriteRow = document.getElementById('jiraWriteToggleRow');
    if (data.jiraBaseUrlConfigured) {
      if (data.jiraConfigured) {
        jiraEl.textContent = 'Connected';
        jiraEl.className   = 'info-val ok';
        jiraBtn.textContent = '✎ Token ändern';
        jiraBtn.classList.add('visible');
      } else {
        jiraEl.textContent = 'Kein Token';
        jiraEl.className   = 'info-val error';
        jiraBtn.textContent = '+ Token eingeben';
        jiraBtn.classList.add('visible');
      }
      if (jiraWriteRow) jiraWriteRow.style.display = 'flex';
    } else {
      jiraEl.textContent = 'Not configured';
      jiraEl.className   = 'info-val error';
      jiraBtn.classList.remove('visible');
      if (jiraWriteRow) jiraWriteRow.style.display = 'none';
    }

    const bbEl  = document.getElementById('infoBitbucket');
    const bbBtn = document.getElementById('btnBitbucketToken');
    if (data.bitbucketBaseUrlConfigured) {
      if (data.bitbucketConfigured) {
        bbEl.textContent = 'Connected';
        bbEl.className   = 'info-val ok';
        bbBtn.textContent = '✎ Token ändern';
        bbBtn.classList.add('visible');
      } else {
        bbEl.textContent = 'Kein Token';
        bbEl.className   = 'info-val error';
        bbBtn.textContent = '+ Token eingeben';
        bbBtn.classList.add('visible');
      }
    } else {
      bbEl.textContent = 'Not configured';
      bbEl.className   = 'info-val error';
      if (bbBtn) bbBtn.classList.remove('visible');
    }

    if (data.hasExport) {
      hasExportData = true;
      setExportButtons(true);
    }

    refreshGithubInfoBox();

    if (!isThinking) setStatusIndicator('ok', 'Ready');
  } catch {
    if (!isThinking) setStatusIndicator('error', 'Server nicht erreichbar');
  }
}

// ── Init ───────────────────────────────────────────────────────────────────

window.addEventListener('load', () => {
  // Wire up chat module with app-level state callbacks
  initChat({
    isThinkingFn:       () => isThinking,
    setThinkingFn:      (v) => setThinking(v),
    setExportEnabledFn: (v) => { hasExportData = v; setExportButtons(v); },
  });

  // Wire up GitHub connector callbacks after loadStatus is in scope
  _githubConnector.init({
    onStatusLoaded: () => loadStatus(),
    onMessage:      (role, text) => appendMessage(role, text),
  });

  // Wire up token modal callbacks
  _jiraTokenModal.init({ onSaved: () => loadStatus() });
  _bitbucketTokenModal.init({ onSaved: () => loadStatus() });

  // Expose functions called from HTML onclick attributes.
  // These will be replaced with addEventListener when each module is extracted.
  Object.assign(window, {
    openGithubConnectModal, closeGithubConnectModal,
    startGithubConnect, copyGithubUserCode, disconnectGithub,
    openJiraTokenModal, closeJiraTokenModal, saveJiraToken, clearJiraToken,
    openBitbucketTokenModal, closeBitbucketTokenModal, saveBitbucketToken, clearBitbucketToken,
    reloadSchema, toggleJiraWriteMode,
  });

  _jiraTokenModal.syncToSession()
    .then(() => syncJiraWriteModeToSession())
    .then(() => _bitbucketTokenModal.syncToSession())
    .then(() => loadStatus());
  setInterval(loadStatus, 15000);

  appendMessage('aigeny',
    'Privet Towarischtsch! Ich bin AIgeny - dein persönlicher Daten-Assistent mit russische Seele.\n\n' +
    'Du kannst mir Fragen zur Datenbank stellen, Berichte anfordern oder Jira-Tickets suchen.\n\n' +
    '**Was kann ich heute für dich tun?**');
});

