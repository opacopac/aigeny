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
import { JiraWriteMode } from './jira-write-mode.js';
import { StatusPanel } from './status-panel.js';
import { SchemaPanel } from './schema-panel.js';

// ── State ──────────────────────────────────────────────────────────────────
let isThinking = false;
let hasExportData = false;

// ── HAL Eye ────────────────────────────────────────────────────────────────
const _halAnimator = new HalEyeAnimator(
  document.getElementById('halEye'),
  document.getElementById('halEyeMini')
);
_halAnimator.start(() => isThinking);

// ── Status Panel ───────────────────────────────────────────────────────────
const _statusPanel = new StatusPanel({
  infoLlm:       document.getElementById('infoLlm'),
  infoModel:     document.getElementById('infoModel'),
  infoTables:    document.getElementById('infoTables'),
  infoDb:        document.getElementById('infoDb'),
  infoJira:      document.getElementById('infoJira'),
  btnJiraToken:  document.getElementById('btnJiraToken'),
  jiraWriteRow:  document.getElementById('jiraWriteToggleRow'),
  infoBitbucket: document.getElementById('infoBitbucket'),
  btnBitbucket:  document.getElementById('btnBitbucketToken'),
  githubInfoRow: document.getElementById('githubInfoRow'),
  statusDot:     document.getElementById('statusDot'),
  statusText:    document.getElementById('statusText'),
  sendBtn:       document.getElementById('sendBtn'),
  stopBtn:       document.getElementById('stopBtn'),
  halStatusText: document.getElementById('halStatusText'),
  btnCsv:        document.getElementById('btnCsv'),
});

// ── Jira Write Mode ────────────────────────────────────────────────────────
const _jiraWriteMode = new JiraWriteMode(
  'aigeny.jiraWriteEnabled',
  '/api/jira/write-mode',
  {
    toggle: document.getElementById('jiraWriteToggle'),
    label:  document.getElementById('jiraWriteLabel'),
  }
);

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

const _schemaPanel = new SchemaPanel({
  btn:        document.querySelector('button[onclick="reloadSchema()"]'),
  infoTables: document.getElementById('infoTables'),
});

async function reloadSchema() {
  await _schemaPanel.reload(appendMessage, () => loadStatus());
}


// ── State helpers ──────────────────────────────────────────────────────────

function setThinking(thinking) {
  isThinking = thinking;
  _statusPanel.setThinking(thinking);
}

function setExportButtons(enabled) {
  _statusPanel.setExportEnabled(enabled);
}

// ── Status polling ─────────────────────────────────────────────────────────

async function loadStatus() {
  try {
    const res  = await fetch('/api/status');
    const data = await res.json();
    _statusPanel.applyStatus(data);
    if (data.hasExport) { hasExportData = true; setExportButtons(true); }
    refreshGithubInfoBox();
    if (!isThinking) _statusPanel.setStatusIndicator('ok', 'Ready');
  } catch {
    if (!isThinking) _statusPanel.setStatusIndicator('error', 'Server nicht erreichbar');
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
    reloadSchema, toggleJiraWriteMode: (v) => _jiraWriteMode.toggle(v),
  });

  _jiraTokenModal.syncToSession()
    .then(() => _jiraWriteMode.syncToSession())
    .then(() => _bitbucketTokenModal.syncToSession())
    .then(() => loadStatus());
  setInterval(loadStatus, 15000);

  appendMessage('aigeny',
    'Privet Towarischtsch! Ich bin AIgeny - dein persönlicher Daten-Assistent mit russische Seele.\n\n' +
    'Du kannst mir Fragen zur Datenbank stellen, Berichte anfordern oder Jira-Tickets suchen.\n\n' +
    '**Was kann ich heute für dich tun?**');
});

