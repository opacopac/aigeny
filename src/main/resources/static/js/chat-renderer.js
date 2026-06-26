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
 *   showJiraConfirmation(desc)      – render a Jira confirm/cancel block
 *   showJiraBatchConfirmation(actions) – render a batch confirm dialog for multiple actions
 *   hasPendingJiraConfirmation()    – true when a confirmation block is visible
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
 * Returns true when at least one Jira confirmation block is currently visible.
 */
export function hasPendingJiraConfirmation() {
  return !!document.querySelector('.jira-confirm-msg');
}

/**
 * Render an inline confirm/cancel block for a pending Jira write action.
 * Called when the SSE stream delivers a {@code confirmation_required} event.
 * The SSE stream stays open; the user's decision is posted to
 * {@code POST /api/jira/confirm-decision} and the stream resumes automatically.
 *
 * @param {string} description – markdown-formatted action description
 */
export function showJiraConfirmation(description) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = 'message aigeny jira-confirm-msg';

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot  = document.createElement('span'); dot.className = 'dot';
  const name = document.createElement('span'); name.textContent = 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';

  bubble.innerHTML = renderMarkdown(
    `⚠️ **Jira Schreibaktion – Bestätigung erforderlich!**\n\n` +
    `${description}\n\n` +
    `Soll ich diese Aktion wirklich ausführen, Towarischtsch?`
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

/**
 * User confirmed the Jira action.
 * Removes the dialog, shows a typing indicator and posts the decision to the backend.
 * The open SSE stream resumes naturally once the backend unblocks.
 */
export async function executeJiraAction(confirmBlock) {
  confirmBlock.querySelectorAll('button').forEach(b => b.disabled = true);
  confirmBlock.remove();
  showTypingIndicator();
  await fetch('/api/jira/confirm-decision', {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ confirmed: true }),
  }).catch(() => {});
}

/**
 * User declined the Jira action.
 * Removes the dialog, shows a typing indicator and posts the decline to the backend.
 * The LLM will be told the action was declined and produce a response.
 */
export async function cancelJiraAction(confirmBlock) {
  confirmBlock.remove();
  showTypingIndicator();
  await fetch('/api/jira/confirm-decision', {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ confirmed: false }),
  }).catch(() => {});
}

// ── Jira batch confirmation block ─────────────────────────────────────────────

/**
 * Render a batch confirm dialog for multiple pending Jira write actions.
 * Called when the SSE stream delivers a {@code batch_confirmation_required} event.
 *
 * @param {Array<{toolCallId: string, toolName: string, description: string}>} actions
 */
export function showJiraBatchConfirmation(actions) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = 'message aigeny jira-confirm-msg jira-batch-confirm-msg';

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot  = document.createElement('span'); dot.className = 'dot';
  const name = document.createElement('span'); name.textContent = 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';

  const titleEl = document.createElement('div');
  titleEl.innerHTML = renderMarkdown(
    `⚠️ **${actions.length} Jira Schreibaktionen – Bestätigung erforderlich!**\n\n` +
    `Soll ich die folgenden Aktionen wirklich ausführen, Towarischtsch?`
  );
  bubble.appendChild(titleEl);

  // Per-action toggle list
  const list = document.createElement('div');
  list.className = 'jira-batch-action-list';
  const decisions = {};
  actions.forEach(action => {
    decisions[action.toolCallId] = true; // default: confirmed

    const row = document.createElement('div');
    row.className = 'jira-batch-action-row';

    const toggle = document.createElement('input');
    toggle.type    = 'checkbox';
    toggle.id      = 'batch-toggle-' + action.toolCallId;
    toggle.checked = true;
    toggle.onchange = () => { decisions[action.toolCallId] = toggle.checked; };

    const label = document.createElement('label');
    label.htmlFor = toggle.id;
    label.innerHTML = renderMarkdown(action.description);

    row.append(toggle, label);
    list.appendChild(row);
  });
  bubble.appendChild(list);

  const btnRow = document.createElement('div');
  btnRow.className = 'jira-confirm-buttons';

  const confirmAllBtn = document.createElement('button');
  confirmAllBtn.className = 'btn btn-confirm';
  confirmAllBtn.textContent = '✔ Da! Alle ausführen';
  confirmAllBtn.onclick = () => submitBatchDecision(div, decisions, true);

  const confirmSelectedBtn = document.createElement('button');
  confirmSelectedBtn.className = 'btn btn-confirm-secondary';
  confirmSelectedBtn.textContent = '✔ Auswahl ausführen';
  confirmSelectedBtn.onclick = () => submitBatchDecision(div, decisions, null);

  const cancelAllBtn = document.createElement('button');
  cancelAllBtn.className = 'btn btn-cancel';
  cancelAllBtn.textContent = '✗ Njet! Alle abbrechen';
  cancelAllBtn.onclick = () => submitBatchDecision(div, decisions, false);

  btnRow.append(confirmAllBtn, confirmSelectedBtn, cancelAllBtn);
  bubble.appendChild(btnRow);

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

/**
 * Submit batch decisions to the backend.
 * @param {HTMLElement}  confirmBlock  – the dialog element to remove
 * @param {Object}       decisions     – map of toolCallId → boolean (per-toggle state)
 * @param {boolean|null} overrideAll   – if true/false, override all decisions; null = use per-toggle
 */
async function submitBatchDecision(confirmBlock, decisions, overrideAll) {
  confirmBlock.querySelectorAll('button').forEach(b => b.disabled = true);
  confirmBlock.remove();
  showTypingIndicator();

  const body = overrideAll !== null
    ? { confirmAll: overrideAll }
    : { decisions };

  await fetch('/api/jira/batch-confirm-decision', {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify(body),
  }).catch(() => {});
}
