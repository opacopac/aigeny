/**
 * chat-stream.js – Chat SSE Stream Communication
 *
 * Single Responsibility: owns the fetch/SSE lifecycle for sending a chat message
 * and cancelling generation.  Contains zero DOM manipulation – that lives in
 * chat-renderer.js.
 *
 * Exports:
 *   initStream(deps)    – inject app-level callbacks (called once by chat.js)
 *   sendMessage()       – POST message, consume SSE stream, delegate rendering
 *   stopGeneration()    – POST /api/chat/cancel
 *
 * Dependencies injected via initStream():
 *   isThinkingFn        – () => boolean
 *   setThinkingFn       – (v: boolean) => void
 *   setExportEnabledFn  – (v: boolean) => void
 *   renderer            – { appendMessage, showTypingIndicator, updateTypingIndicator,
 *                           finalizeTypingIndicator, removeTypingIndicator,
 *                           showJiraConfirmation }
 */

// ── Module-level state (set once by initStream) ───────────────────────────────
let _isThinkingFn       = () => false;
let _setThinkingFn      = (_v) => {};
let _setExportEnabledFn = (_v) => {};
let _renderer           = {};
let _currentAbortCtrl   = null;

/**
 * Inject app-level callbacks and the renderer.  Call once after the DOM is ready.
 */
export function initStream({ isThinkingFn, setThinkingFn, setExportEnabledFn, renderer }) {
  _isThinkingFn       = isThinkingFn;
  _setThinkingFn      = setThinkingFn;
  _setExportEnabledFn = setExportEnabledFn;
  _renderer           = renderer;
}

// ── SSE event handler map ─────────────────────────────────────────────────────
// O-2: Replace if-else chain with a handler map.  Adding a new SSE event type only
// requires registering a new entry here – no modification of processStream() needed.
function buildSseHandlers() {
  return {
    tool_call: (data) => {
      _renderer.updateTypingIndicator(data.toolName, data.description);
    },
    intermediate: (data) => {
      _renderer.finalizeTypingIndicator();
      _renderer.appendMessage('aigeny', data.response);
      _renderer.showTypingIndicator();
    },
    confirmation_required: (data) => {
      // Pause visual: finalize the current tool-call list, show the confirmation dialog.
      // The SSE stream stays open – the orchestration thread is blocked waiting for the
      // user's decision via POST /api/jira/confirm-decision.
      _renderer.finalizeTypingIndicator();
      _renderer.showJiraConfirmation(data.description);
    },
    batch_confirmation_required: (data) => {
      // Multiple write actions in one LLM response: show one combined dialog.
      // The SSE stream stays open – the orchestration thread is blocked waiting for the
      // user's decisions via POST /api/jira/batch-confirm-decision.
      _renderer.finalizeTypingIndicator();
      _renderer.showJiraBatchConfirmation(data.actions);
    },
    done: (data) => {
      _renderer.finalizeTypingIndicator();
      if (data.response)  _renderer.appendMessage('aigeny', data.response);
      if (data.hasExport) _setExportEnabledFn(true);
    },
    cancelled: () => {
      _renderer.removeTypingIndicator();
      _renderer.appendMessage('aigeny', '_Abgebrochen, Towarischtsch. AIgeny steht wieder bereit._');
    },
    error: (data) => {
      _renderer.removeTypingIndicator();
      _renderer.appendMessage('aigeny', 'Njet! Fehler, Towarischtsch: ' + (data.message || '?'));
    },
  };
}

// ── Communication ─────────────────────────────────────────────────────────────

/**
 * Process a readable SSE stream, delegating each event to the registered handler.
 * Unknown event types are silently ignored (optional-chaining on the handler map).
 * @param {ReadableStream} responseBody
 */
async function processStream(responseBody) {
  const handlers = buildSseHandlers();
  const reader  = responseBody.getReader();
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

      handlers[data.type]?.(data);
    }
  }
}

export async function sendMessage() {
  if (_isThinkingFn()) return;

  // Block new messages while a Jira confirmation (single or batch) is pending
  if (_renderer.hasPendingJiraConfirmation?.()) {
    _renderer.appendMessage('aigeny',
      '_Es gibt eine ausstehende Jira-Aktion, Towarischtsch. ' +
      'Bitte erst bestätigen oder ablehnen!_');
    return;
  }

  const input   = document.getElementById('userInput');
  const message = input.value.trim();
  if (!message) return;

  input.value = '';
  _renderer.appendMessage('user', message);
  _setThinkingFn(true);
  _renderer.showTypingIndicator();

  _currentAbortCtrl = new AbortController();

  try {
    const response = await fetch('/api/chat/stream', {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ message }),
      signal:  _currentAbortCtrl.signal,
    });
    await processStream(response.body);
  } catch (err) {
    if (err.name === 'AbortError') {
      _renderer.removeTypingIndicator();
    } else {
      _renderer.removeTypingIndicator();
      _renderer.appendMessage('aigeny', 'Njet! Netzwerkfehler, Towarischtsch: ' + err.message);
    }
  } finally {
    _currentAbortCtrl = null;
    _setThinkingFn(false);
  }
}

export async function stopGeneration() {
  if (!_isThinkingFn()) return;
  fetch('/api/chat/cancel', { method: 'POST' }).catch(() => {});
}
