/* ─────────────────────────────────────────────────────────────────────────
   AIgeny - frontend JavaScript
   - Chat send / receive
   - Export buttons
   - Status polling
   ───────────────────────────────────────────────────────────────────────── */

import { HalEyeAnimator } from './hal-eye.js';
import { initChat, appendMessage } from './chat.js';
import { GithubConnector } from './github-connect.js';

// ── State ──────────────────────────────────────────────────────────────────
let isThinking = false;
let hasExportData = false;
const JIRA_TOKEN_KEY      = 'aigeny.jiraToken';
const JIRA_WRITE_KEY      = 'aigeny.jiraWriteEnabled';
const BITBUCKET_TOKEN_KEY = 'aigeny.bitbucketToken';

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

// ── Jira Token Modal ───────────────────────────────────────────────────────

function openJiraTokenModal() {
  const stored = localStorage.getItem(JIRA_TOKEN_KEY) || '';
  document.getElementById('jiraTokenInput').value = stored;
  document.getElementById('jiraTokenHint').textContent = stored ? '✔ Token ist gespeichert.' : '';
  document.getElementById('jiraTokenHint').className = stored ? 'modal-hint ok' : 'modal-hint';
  document.getElementById('jiraTokenModal').style.display = 'flex';
  setTimeout(() => document.getElementById('jiraTokenInput').focus(), 50);
}

function closeJiraTokenModal(e) {
  if (e && e.target !== document.getElementById('jiraTokenModal')) return;
  document.getElementById('jiraTokenModal').style.display = 'none';
}

async function saveJiraToken() {
  const token = document.getElementById('jiraTokenInput').value.trim();
  const hint  = document.getElementById('jiraTokenHint');
  try {
    const res = await fetch('/api/jira/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token })
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (token) {
      localStorage.setItem(JIRA_TOKEN_KEY, token);
      hint.textContent = '✔ Token gespeichert! Da, choroscho!';
      hint.className = 'modal-hint ok';
    } else {
      localStorage.removeItem(JIRA_TOKEN_KEY);
      hint.textContent = 'Token gelöscht.';
      hint.className = 'modal-hint';
    }
    setTimeout(() => {
      document.getElementById('jiraTokenModal').style.display = 'none';
      loadStatus();
    }, 900);
  } catch (err) {
    hint.textContent = 'Njet! Fehler: ' + err.message;
    hint.className = 'modal-hint error';
  }
}

async function clearJiraToken() {
  localStorage.removeItem(JIRA_TOKEN_KEY);
  await fetch('/api/jira/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: '' })
  });
  document.getElementById('jiraTokenInput').value = '';
  const hint = document.getElementById('jiraTokenHint');
  hint.textContent = 'Token gelöscht.';
  hint.className = 'modal-hint';
  setTimeout(() => {
    document.getElementById('jiraTokenModal').style.display = 'none';
    loadStatus();
  }, 600);
}

/** Send stored Jira token to backend session (called on page load) */
async function syncJiraTokenToSession() {
  const token = localStorage.getItem(JIRA_TOKEN_KEY);
  if (!token) return;
  try {
    await fetch('/api/jira/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token })
    });
  } catch (e) { /* ignore */ }
}

// Close modal on Escape
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') {
    document.getElementById('jiraTokenModal').style.display = 'none';
    document.getElementById('bitbucketTokenModal').style.display = 'none';
    const gh = document.getElementById('githubConnectModal');
    if (gh) gh.style.display = 'none';
  }
});

// ── Bitbucket Token Modal ──────────────────────────────────────────────────

function openBitbucketTokenModal() {
  const stored = localStorage.getItem(BITBUCKET_TOKEN_KEY) || '';
  document.getElementById('bitbucketTokenInput').value = stored;
  document.getElementById('bitbucketTokenHint').textContent = stored ? '✔ Token ist gespeichert.' : '';
  document.getElementById('bitbucketTokenHint').className = stored ? 'modal-hint ok' : 'modal-hint';
  document.getElementById('bitbucketTokenModal').style.display = 'flex';
  setTimeout(() => document.getElementById('bitbucketTokenInput').focus(), 50);
}

function closeBitbucketTokenModal(e) {
  if (e && e.target !== document.getElementById('bitbucketTokenModal')) return;
  document.getElementById('bitbucketTokenModal').style.display = 'none';
}

async function saveBitbucketToken() {
  const token = document.getElementById('bitbucketTokenInput').value.trim();
  const hint  = document.getElementById('bitbucketTokenHint');
  try {
    const res = await fetch('/api/bitbucket/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token })
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    if (token) {
      localStorage.setItem(BITBUCKET_TOKEN_KEY, token);
      hint.textContent = '✔ Token gespeichert! Da, choroscho!';
      hint.className = 'modal-hint ok';
    } else {
      localStorage.removeItem(BITBUCKET_TOKEN_KEY);
      hint.textContent = 'Token gelöscht.';
      hint.className = 'modal-hint';
    }
    setTimeout(() => {
      document.getElementById('bitbucketTokenModal').style.display = 'none';
      loadStatus();
    }, 900);
  } catch (err) {
    hint.textContent = 'Njet! Fehler: ' + err.message;
    hint.className = 'modal-hint error';
  }
}

async function clearBitbucketToken() {
  localStorage.removeItem(BITBUCKET_TOKEN_KEY);
  await fetch('/api/bitbucket/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token: '' })
  });
  document.getElementById('bitbucketTokenInput').value = '';
  const hint = document.getElementById('bitbucketTokenHint');
  hint.textContent = 'Token gelöscht.';
  hint.className = 'modal-hint';
  setTimeout(() => {
    document.getElementById('bitbucketTokenModal').style.display = 'none';
    loadStatus();
  }, 600);
}

/** Send stored Bitbucket token to backend session (called on page load) */
async function syncBitbucketTokenToSession() {
  const token = localStorage.getItem(BITBUCKET_TOKEN_KEY);
  if (!token) return;
  try {
    await fetch('/api/bitbucket/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token })
    });
  } catch (e) { /* ignore */ }
}

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

  // Expose functions called from HTML onclick attributes.
  // These will be replaced with addEventListener when each module is extracted.
  Object.assign(window, {
    openGithubConnectModal, closeGithubConnectModal,
    startGithubConnect, copyGithubUserCode, disconnectGithub,
    openJiraTokenModal, closeJiraTokenModal, saveJiraToken, clearJiraToken,
    openBitbucketTokenModal, closeBitbucketTokenModal, saveBitbucketToken, clearBitbucketToken,
    reloadSchema, toggleJiraWriteMode,
  });

  syncJiraTokenToSession()
    .then(() => syncJiraWriteModeToSession())
    .then(() => syncBitbucketTokenToSession())
    .then(() => loadStatus());
  setInterval(loadStatus, 15000);

  appendMessage('aigeny',
    'Privet Towarischtsch! Ich bin AIgeny - dein persönlicher Daten-Assistent mit russische Seele.\n\n' +
    'Du kannst mir Fragen zur Datenbank stellen, Berichte anfordern oder Jira-Tickets suchen.\n\n' +
    '**Was kann ich heute für dich tun?**');
});

