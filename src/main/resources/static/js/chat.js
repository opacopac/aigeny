/**
 * chat.js – Chat Coordinator
 *
 * Single Responsibility: wires together the two sub-modules and owns the
 * remaining chat-level actions (clearChat, exportData).
 *
 *   chat-renderer.js  – all DOM creation / mutation
 *   chat-stream.js    – fetch / SSE stream lifecycle
 *
 * Re-exports every public symbol so that existing consumers (app.js, tests)
 * do not need to change their import paths.
 *
 * Public API (unchanged):
 *   initChat(deps)           – inject app-level callbacks and wire DOM listeners
 *   appendMessage(role,text) – append a rendered message bubble
 *   showTypingIndicator()    – show the bouncing-dots + tool-call list
 *   updateTypingIndicator()  – add a tool-call entry to the list
 *   finalizeTypingIndicator()– hide dots, keep tool-call list, or remove entirely
 *   removeTypingIndicator()  – force-remove the indicator
 *   showJiraConfirmation()        – render a Jira confirm/cancel block
 *   hasPendingJiraConfirmation() – true when a confirmation block is visible
 *   executeJiraAction()          – POST /api/jira/confirm
 *   cancelJiraAction()           – POST /api/jira/cancel
 *   sendMessage()            – POST chat message, consume SSE stream
 *   stopGeneration()         – POST /api/chat/cancel
 *   clearChat()              – POST /api/chat/clear, reset UI
 *   exportData(format)       – navigate to export URL
 */

// ── Sub-module imports ────────────────────────────────────────────────────────

import * as Renderer from './chat-renderer.js';
import { initStream, sendMessage, stopGeneration, handleJiraConfirmAndResume } from './chat-stream.js';

// ── Re-exports (backwards-compatibility) ─────────────────────────────────────

export const appendMessage              = Renderer.appendMessage;
export const showTypingIndicator        = Renderer.showTypingIndicator;
export const updateTypingIndicator      = Renderer.updateTypingIndicator;
export const finalizeTypingIndicator    = Renderer.finalizeTypingIndicator;
export const removeTypingIndicator      = Renderer.removeTypingIndicator;
export const showJiraConfirmation       = Renderer.showJiraConfirmation;
export const hasPendingJiraConfirmation = Renderer.hasPendingJiraConfirmation;
export const executeJiraAction          = Renderer.executeJiraAction;
export const cancelJiraAction           = Renderer.cancelJiraAction;
export { sendMessage, stopGeneration, handleJiraConfirmAndResume };

// ── Module-level state ────────────────────────────────────────────────────────

let _setExportEnabledFn = (_v) => {};

// ── Initialisation ────────────────────────────────────────────────────────────

/**
 * Inject app-level callbacks and register all DOM event listeners for the
 * chat panel.  Call once after the DOM is ready.
 *
 * @param {Object} deps
 * @param {()=>boolean}       deps.isThinkingFn       – returns current thinking state
 * @param {(v:boolean)=>void} deps.setThinkingFn      – update thinking state + UI
 * @param {(v:boolean)=>void} deps.setExportEnabledFn – enable/disable export button
 */
export function initChat({ isThinkingFn, setThinkingFn, setExportEnabledFn }) {
  _setExportEnabledFn = setExportEnabledFn;

  // Wire the confirm-and-resume handler into the renderer (Dependency Inversion)
  Renderer.setConfirmHandler(handleJiraConfirmAndResume);

  // Forward deps + renderer to the stream module
  initStream({
    isThinkingFn,
    setThinkingFn,
    setExportEnabledFn,
    hasPendingJiraConfirmationFn: Renderer.hasPendingJiraConfirmation,
    renderer: {
      appendMessage:           Renderer.appendMessage,
      showTypingIndicator:     Renderer.showTypingIndicator,
      updateTypingIndicator:   Renderer.updateTypingIndicator,
      finalizeTypingIndicator: Renderer.finalizeTypingIndicator,
      removeTypingIndicator:   Renderer.removeTypingIndicator,
      showJiraConfirmation:    Renderer.showJiraConfirmation,
    },
  });

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

// ── Chat-level actions ────────────────────────────────────────────────────────

export async function clearChat() {
  await fetch('/api/chat/clear', { method: 'POST' });
  document.getElementById('chatMessages').innerHTML = '';
  _setExportEnabledFn(false);
  Renderer.appendMessage('aigeny',
    'Da, Chat wurde geleert, Towarischtsch! AIgeny ist bereit für neue Fragen. ' +
    'Was möchtest du heute aus Datenbank wissen?');
}

export function exportData(format) {
  window.location.href = '/api/export/' + format;
}
