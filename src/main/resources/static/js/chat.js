/**
 * chat.js – Chat rendering & communication
 *
 * Exports:
 *   initChat(deps)           – inject app-level callbacks and wire DOM listeners
 *   appendMessage(role,text) – append a rendered message bubble
 *   showTypingIndicator()    – show the bouncing-dots + tool-call list
 *   updateTypingIndicator()  – add a tool-call entry to the list
 *   finalizeTypingIndicator()– hide dots, keep tool-call list, or remove entirely
 *   removeTypingIndicator()  – force-remove the indicator
 *   showJiraConfirmation()   – render a Jira confirm/cancel block
 *   executeJiraAction()      – POST /api/jira/confirm
 *   cancelJiraAction()       – POST /api/jira/cancel
 *   sendMessage()            – POST chat message, consume SSE stream
 *   stopGeneration()         – POST /api/chat/cancel
 *   clearChat()              – POST /api/chat/clear, reset UI
 *   exportData(format)       – navigate to export URL
 */

import { renderMarkdown } from './markdown.js';

// ── Module-level state ───────────────────────────────────────────────────────
// Set once by initChat(); never accessed directly from outside.
let _isThinkingFn       = () => false;
let _setThinkingFn      = (_v) => {};
let _setExportEnabledFn = (_v) => {};
let _currentAbortCtrl   = null;

// ── Initialisation ───────────────────────────────────────────────────────────

/**
 * Inject app-level callbacks and register all DOM event listeners for the
 * chat panel. Call once after the DOM is ready.
 *
 * @param {Object} deps
 * @param {()=>boolean}    deps.isThinkingFn       – returns current thinking state
 * @param {(v:boolean)=>void} deps.setThinkingFn   – update thinking state + UI
 * @param {(v:boolean)=>void} deps.setExportEnabledFn – enable/disable export button
 */
export function initChat({ isThinkingFn, setThinkingFn, setExportEnabledFn }) {
  _isThinkingFn       = isThinkingFn;
  _setThinkingFn      = setThinkingFn;
  _setExportEnabledFn = setExportEnabledFn;

  document.getElementById('sendBtn')
    ?.addEventListener('click', sendMessage);
  document.getElementById('stopBtn')
    ?.addEventListener('click', stopGeneration);
  document.getElementById('btnCsv')
    ?.addEventListener('click', () => exportData('csv'));
  document.getElementById('btnClearChat')
    ?.addEventListener('click', clearChat);

  document.getElementById('userInput')
    ?.addEventListener('keydown', e => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });
}

// ── Rendering ────────────────────────────────────────────────────────────────

/**
 * Append a user or AI message bubble to the chat.
 * @param {'user'|'aigeny'} role
 * @param {string} text – raw Markdown
 * @returns {HTMLElement} the created message element
 */
export function appendMessage(role, text) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = 'message ' + role;

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot  = document.createElement('span');  dot.className = 'dot';
  const name = document.createElement('span');  name.textContent = role === 'user' ? 'Du' : 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  bubble.innerHTML = renderMarkdown(text);

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
  return div;
}

/** Show the bouncing-dots typing indicator and an empty tool-call list. */
export function showTypingIndicator() {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.id        = 'typingIndicator';
  div.className = 'message aigeny';

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot  = document.createElement('span'); dot.className = 'dot';
  const name = document.createElement('span'); name.textContent = 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  bubble.innerHTML =
    '<div class="typing-indicator" id="typingDots"><span></span><span></span><span></span></div>' +
    '<ul class="tool-call-list" id="toolCallList"></ul>';

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

/**
 * Add a tool-call entry to the visible list inside the typing indicator.
 * @param {string} toolName
 * @param {string} description
 */
export function updateTypingIndicator(toolName, description) {
  const list = document.getElementById('toolCallList');
  if (!list) return;

  const li       = document.createElement('li');  li.className = 'tool-call-item';
  const nameSpan = document.createElement('span'); nameSpan.className = 'tool-call-name'; nameSpan.textContent = toolName;
  const descSpan = document.createElement('span'); descSpan.className = 'tool-call-desc'; descSpan.textContent = description;
  li.append(nameSpan, descSpan);
  list.appendChild(li);

  const msgs = document.getElementById('chatMessages');
  if (msgs) msgs.scrollTop = msgs.scrollHeight;
}

/**
 * Hide the bouncing dots and keep (or remove) the tool-call list:
 * - If the list is empty  → remove the whole indicator block
 * - If the list has items → keep it visible but strip its IDs so the
 *   next request gets fresh DOM references
 */
export function finalizeTypingIndicator() {
  const indicator = document.getElementById('typingIndicator');
  const dots      = document.getElementById('typingDots');
  const list      = document.getElementById('toolCallList');

  if (dots) dots.style.display = 'none';

  if (list && list.children.length === 0) {
    indicator?.remove();
  } else {
    indicator?.removeAttribute('id');
    dots?.removeAttribute('id');
    list?.removeAttribute('id');
  }
}

/** Force-remove the typing indicator (e.g. on abort or error). */
export function removeTypingIndicator() {
  document.getElementById('typingIndicator')?.remove();
}

// ── Jira confirmation ────────────────────────────────────────────────────────

/**
 * Render an inline confirm/cancel block for a pending Jira write action.
 * @param {{ description: string }} pendingAction
 */
export function showJiraConfirmation(pendingAction) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = 'message aigeny jira-confirm-msg';
  div.id        = 'jiraConfirmBlock';

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot  = document.createElement('span'); dot.className = 'dot';
  const name = document.createElement('span'); name.textContent = 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';

  const plural = pendingAction.description.startsWith('**') ? 'en' : '';
  bubble.innerHTML = renderMarkdown(
    `⚠️ **Jira Schreibaktion${plural} – Bestätigung erforderlich!**\n\n` +
    `${pendingAction.description}\n\n` +
    `Soll ich diese Aktion${plural} wirklich ausführen, Towarischtsch?`
  );

  const btnRow = document.createElement('div');
  btnRow.className = 'jira-confirm-buttons';

  const confirmBtn = document.createElement('button');
  confirmBtn.className = 'btn btn-confirm';
  confirmBtn.textContent = '✔ Da! Ausführen';
  confirmBtn.onclick = () => executeJiraAction(div);

  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn btn-cancel';
  cancelBtn.textContent = '✗ Njet! Abbrechen';
  cancelBtn.onclick = () => cancelJiraAction(div);

  btnRow.append(confirmBtn, cancelBtn);
  bubble.appendChild(btnRow);

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

export async function executeJiraAction(confirmBlock) {
  confirmBlock.querySelectorAll('button').forEach(b => b.disabled = true);
  try {
    const res  = await fetch('/api/jira/confirm', { method: 'POST' });
    const data = await res.json();
    confirmBlock.remove();
    appendMessage('aigeny', data.result || 'Aktion ausgeführt, da!');
  } catch (err) {
    confirmBlock.remove();
    appendMessage('aigeny', 'Njet! Netzwerkfehler: ' + err.message);
  }
}

export async function cancelJiraAction(confirmBlock) {
  await fetch('/api/jira/cancel', { method: 'POST' }).catch(() => {});
  confirmBlock.remove();
  appendMessage('aigeny', 'Njet! Aktion wurde abgebrochen, Towarischtsch. Choroscho, keine Änderungen gemacht, da!');
}

// ── Communication ────────────────────────────────────────────────────────────

export async function sendMessage() {
  if (_isThinkingFn()) return;
  const input   = document.getElementById('userInput');
  const message = input.value.trim();
  if (!message) return;

  input.value = '';
  appendMessage('user', message);
  _setThinkingFn(true);
  showTypingIndicator();

  _currentAbortCtrl = new AbortController();

  try {
    const response = await fetch('/api/chat/stream', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ message }),
      signal:  _currentAbortCtrl.signal,
    });

    const reader  = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer    = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const parts = buffer.split('\n\n');
      buffer = parts.pop();

      for (const part of parts) {
        const dataLine = part.split('\n').find(l => l.startsWith('data:'));
        if (!dataLine) continue;
        let data;
        try { data = JSON.parse(dataLine.slice(5).trim()); } catch { continue; }

        if (data.type === 'tool_call') {
          updateTypingIndicator(data.toolName, data.description);
        } else if (data.type === 'intermediate') {
          finalizeTypingIndicator();
          appendMessage('aigeny', data.response);
          showTypingIndicator();
        } else if (data.type === 'done') {
          finalizeTypingIndicator();
          if (data.response)     appendMessage('aigeny', data.response);
          if (data.pendingAction) showJiraConfirmation(data.pendingAction);
          if (data.hasExport)    _setExportEnabledFn(true);
        } else if (data.type === 'cancelled') {
          removeTypingIndicator();
          appendMessage('aigeny', '_Abgebrochen, Towarischtsch. AIgeny steht wieder bereit._');
        } else if (data.type === 'error') {
          removeTypingIndicator();
          appendMessage('aigeny', 'Njet! Fehler, Towarischtsch: ' + (data.message || '?'));
        }
      }
    }
  } catch (err) {
    if (err.name === 'AbortError') {
      removeTypingIndicator();
    } else {
      removeTypingIndicator();
      appendMessage('aigeny', 'Njet! Netzwerkfehler, Towarischtsch: ' + err.message);
    }
  } finally {
    _currentAbortCtrl = null;
    _setThinkingFn(false);
  }
}

export async function stopGeneration() {
  if (!_isThinkingFn()) return;
  // Do NOT abort the fetch – let the backend close the stream gracefully.
  fetch('/api/chat/cancel', { method: 'POST' }).catch(() => {});
}

export async function clearChat() {
  await fetch('/api/chat/clear', { method: 'POST' });
  document.getElementById('chatMessages').innerHTML = '';
  _setExportEnabledFn(false);
  appendMessage('aigeny',
    'Da, Chat wurde geleert, Towarischtsch! AIgeny ist bereit für neue Fragen. ' +
    'Was möchtest du heute aus Datenbank wissen?');
}

export function exportData(format) {
  window.location.href = '/api/export/' + format;
}

