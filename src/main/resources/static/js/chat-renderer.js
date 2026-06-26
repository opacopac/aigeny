/**
 * chat-renderer.js – Chat DOM Rendering
 *
 * Single Responsibility: owns ALL DOM creation and mutation for the chat panel.
 * Contains zero fetch/network calls – those live in chat-stream.js.
 *
 * Exports (pure-ish DOM builders, no module-level state):
 *   appendMessage(role, text)       – append a rendered message bubble
 *   showTypingIndicator()           – insert bouncing-dots + empty tool-call list
 *   updateTypingIndicator(name,desc)– add one tool-call entry to the list
 *   finalizeTypingIndicator()       – hide dots, keep/remove tool-call list
 *   removeTypingIndicator()         – force-remove the indicator
 *   showJiraConfirmation(action)       – render a Jira confirm/cancel block
 *   hasPendingJiraConfirmation()      – true when a confirmation block is visible
 *   setConfirmHandler(fn)             – inject the confirm-and-resume handler (DIP)
 *   executeJiraAction(block)          – POST /api/jira/confirm-stream via injected handler
 *   cancelJiraAction(block)           – POST /api/jira/cancel, update DOM
 */

import { renderMarkdown } from './markdown.js';

// ── Message bubbles ───────────────────────────────────────────────────────────

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

// ── Typing indicator ──────────────────────────────────────────────────────────

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

// ── Jira confirmation block ───────────────────────────────────────────────────

/**
 * Injected handler for confirm-and-resume (set by chat.js coordinator).
 * Receives the confirmBlock div, executes the pending Jira actions via SSE and
 * continues the LLM conversation.  Set once at startup via setConfirmHandler().
 * @type {((block: HTMLElement) => Promise<void>)|null}
 */
let _confirmHandler = null;

/**
 * Inject the confirm-and-resume handler from the stream module.
 * Must be called once during initialisation (DIP – renderer does not import stream).
 * @param {(block: HTMLElement) => Promise<void>} fn
 */
export function setConfirmHandler(fn) {
  _confirmHandler = fn;
}

/**
 * Render an inline confirm/cancel block for a pending Jira write action.
 * @param {{ description: string }} pendingAction
 */
/**
 * Returns true when at least one Jira confirmation block is currently visible.
 * Used by the stream module to block new messages while confirmation is pending.
 */
export function hasPendingJiraConfirmation() {
  return !!document.querySelector('.jira-confirm-msg');
}

export function showJiraConfirmation(pendingAction) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  // No fixed id – multiple blocks could theoretically exist across turns.
  // Identification is done via the .jira-confirm-msg CSS class.
  div.className = 'message aigeny jira-confirm-msg';

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
  if (_confirmHandler) {
    // Preferred path: hand off to the stream module which calls /api/jira/confirm-stream
    // and continues the LLM conversation so multi-step plans can finish.
    await _confirmHandler(confirmBlock);
  } else {
    // Fallback (should not occur in production – handler is always set by chat.js).
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
}

export async function cancelJiraAction(confirmBlock) {
  await fetch('/api/jira/cancel', { method: 'POST' }).catch(() => {});
  confirmBlock.remove();
  appendMessage('aigeny', 'Njet! Aktion wurde abgebrochen, Towarischtsch. Choroscho, keine Änderungen gemacht, da!');
}

