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
 *
 * @param {{
 *   isThinkingFn:       () => boolean,
 *   setThinkingFn:      (v: boolean) => void,
 *   setExportEnabledFn: (v: boolean) => void,
 *   renderer: {
 *     appendMessage:           Function,
 *     showTypingIndicator:     Function,
 *     updateTypingIndicator:   Function,
 *     finalizeTypingIndicator: Function,
 *     removeTypingIndicator:   Function,
 *     showJiraConfirmation:    Function,
 *   },
 * }} deps
 */
export function initStream({ isThinkingFn, setThinkingFn, setExportEnabledFn, renderer }) {
  _isThinkingFn       = isThinkingFn;
  _setThinkingFn      = setThinkingFn;
  _setExportEnabledFn = setExportEnabledFn;
  _renderer           = renderer;
}

// ── Communication ─────────────────────────────────────────────────────────────

export async function sendMessage() {
  if (_isThinkingFn()) return;
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
          _renderer.updateTypingIndicator(data.toolName, data.description);
        } else if (data.type === 'intermediate') {
          _renderer.finalizeTypingIndicator();
          _renderer.appendMessage('aigeny', data.response);
          _renderer.showTypingIndicator();
        } else if (data.type === 'done') {
          _renderer.finalizeTypingIndicator();
          if (data.response)      _renderer.appendMessage('aigeny', data.response);
          if (data.pendingAction) _renderer.showJiraConfirmation(data.pendingAction);
          if (data.hasExport)     _setExportEnabledFn(true);
        } else if (data.type === 'cancelled') {
          _renderer.removeTypingIndicator();
          _renderer.appendMessage('aigeny', '_Abgebrochen, Towarischtsch. AIgeny steht wieder bereit._');
        } else if (data.type === 'error') {
          _renderer.removeTypingIndicator();
          _renderer.appendMessage('aigeny', 'Njet! Fehler, Towarischtsch: ' + (data.message || '?'));
        }
      }
    }
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
  // Do NOT abort the fetch – let the backend close the stream gracefully.
  fetch('/api/chat/cancel', { method: 'POST' }).catch(() => {});
}

