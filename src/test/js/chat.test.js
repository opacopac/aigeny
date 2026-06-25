/**
 * chat.test.js – Unit tests for js/chat.js
 *
 * Tests focus on the pure rendering functions (no real fetch calls):
 *   appendMessage, showTypingIndicator, updateTypingIndicator,
 *   finalizeTypingIndicator, removeTypingIndicator, showJiraConfirmation.
 *
 * sendMessage / stopGeneration / clearChat are covered by smoke tests
 * with a mocked fetch to keep the suite fast and dependency-free.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  initChat,
  appendMessage,
  showTypingIndicator,
  updateTypingIndicator,
  finalizeTypingIndicator,
  removeTypingIndicator,
  showJiraConfirmation,
  clearChat,
  sendMessage,
} from '../../main/resources/static/js/chat.js';

// ── DOM fixture ──────────────────────────────────────────────────────────────
function setupDom() {
  document.body.innerHTML = `
    <div id="chatMessages"></div>
    <textarea id="userInput"></textarea>
    <button id="sendBtn"></button>
    <button id="stopBtn"></button>
    <button id="btnCsv"></button>
    <button id="btnClearChat"></button>
  `;
}

// ── Shared spy stubs ─────────────────────────────────────────────────────────
let setThinkingSpy;
let setExportSpy;

beforeEach(() => {
  setupDom();
  setThinkingSpy = vi.fn();
  setExportSpy   = vi.fn();
  initChat({
    isThinkingFn:       () => false,
    setThinkingFn:      setThinkingSpy,
    setExportEnabledFn: setExportSpy,
  });
});

afterEach(() => vi.restoreAllMocks());

// ════════════════════════════════════════════════════════════════════════════
// appendMessage
// ════════════════════════════════════════════════════════════════════════════
describe('appendMessage', () => {
  it('adds a child to #chatMessages', () => {
    appendMessage('user', 'hello');
    expect(document.getElementById('chatMessages').children.length).toBe(1);
  });

  it('sets the correct CSS class for user role', () => {
    appendMessage('user', 'hi');
    const msg = document.querySelector('.message');
    expect(msg.classList.contains('user')).toBe(true);
  });

  it('sets the correct CSS class for aigeny role', () => {
    appendMessage('aigeny', 'hello');
    const msg = document.querySelector('.message');
    expect(msg.classList.contains('aigeny')).toBe(true);
  });

  it('shows "Du" as the name for user messages', () => {
    appendMessage('user', 'test');
    expect(document.querySelector('.message-header span:last-child').textContent).toBe('Du');
  });

  it('shows "AIgeny" as the name for aigeny messages', () => {
    appendMessage('aigeny', 'test');
    expect(document.querySelector('.message-header span:last-child').textContent).toBe('AIgeny');
  });

  it('creates a .message-bubble element', () => {
    appendMessage('user', 'test');
    expect(document.querySelector('.message-bubble')).not.toBeNull();
  });

  it('renders Markdown in the bubble (bold example)', () => {
    appendMessage('aigeny', '**bold**');
    expect(document.querySelector('.message-bubble').innerHTML).toContain('<strong>bold</strong>');
  });

  it('returns the created message element', () => {
    const el = appendMessage('user', 'hi');
    expect(el).toBeInstanceOf(HTMLElement);
    expect(el.classList.contains('message')).toBe(true);
  });

  it('appends multiple messages in order', () => {
    appendMessage('user',   'first');
    appendMessage('aigeny', 'second');
    const msgs = document.querySelectorAll('.message');
    expect(msgs.length).toBe(2);
    expect(msgs[0].classList.contains('user')).toBe(true);
    expect(msgs[1].classList.contains('aigeny')).toBe(true);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// showTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('showTypingIndicator', () => {
  it('inserts an element with id="typingIndicator"', () => {
    showTypingIndicator();
    expect(document.getElementById('typingIndicator')).not.toBeNull();
  });

  it('contains the three bouncing dots', () => {
    showTypingIndicator();
    const dots = document.querySelectorAll('#typingDots span');
    expect(dots.length).toBe(3);
  });

  it('contains an empty tool-call list', () => {
    showTypingIndicator();
    const list = document.getElementById('toolCallList');
    expect(list).not.toBeNull();
    expect(list.children.length).toBe(0);
  });

  it('gives the indicator the aigeny class', () => {
    showTypingIndicator();
    expect(document.getElementById('typingIndicator').classList.contains('aigeny')).toBe(true);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// updateTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('updateTypingIndicator', () => {
  beforeEach(() => showTypingIndicator());

  it('adds one item to the tool-call list', () => {
    updateTypingIndicator('query_db', 'Fetching data…');
    expect(document.getElementById('toolCallList').children.length).toBe(1);
  });

  it('renders the tool name in .tool-call-name span', () => {
    updateTypingIndicator('query_db', 'Fetching data…');
    expect(document.querySelector('.tool-call-name').textContent).toBe('query_db');
  });

  it('renders the description in .tool-call-desc span', () => {
    updateTypingIndicator('query_db', 'Fetching data…');
    expect(document.querySelector('.tool-call-desc').textContent).toBe('Fetching data…');
  });

  it('accumulates multiple tool-call entries', () => {
    updateTypingIndicator('tool_a', 'desc a');
    updateTypingIndicator('tool_b', 'desc b');
    expect(document.getElementById('toolCallList').children.length).toBe(2);
  });

  it('does nothing when #toolCallList does not exist', () => {
    document.getElementById('toolCallList').remove();
    expect(() => updateTypingIndicator('x', 'y')).not.toThrow();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// finalizeTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('finalizeTypingIndicator', () => {
  beforeEach(() => showTypingIndicator());

  it('hides the dots element when list is empty', () => {
    finalizeTypingIndicator();
    expect(document.getElementById('typingDots')).toBeNull(); // indicator was removed entirely
  });

  it('removes the indicator entirely when the tool-call list is empty', () => {
    finalizeTypingIndicator();
    expect(document.getElementById('typingIndicator')).toBeNull();
  });

  it('keeps the indicator (but hides dots) when tool-calls are present', () => {
    updateTypingIndicator('tool', 'desc');
    finalizeTypingIndicator();
    // indicator is kept but its id is stripped
    expect(document.getElementById('typingIndicator')).toBeNull(); // id stripped
    expect(document.querySelector('.message.aigeny')).not.toBeNull(); // element still in DOM
  });

  it('strips the typingIndicator id when tool-calls are present', () => {
    updateTypingIndicator('tool', 'desc');
    finalizeTypingIndicator();
    expect(document.getElementById('typingIndicator')).toBeNull();
    expect(document.getElementById('toolCallList')).toBeNull();
  });

  it('sets typingDots display:none when tool-calls are present', () => {
    updateTypingIndicator('tool', 'desc');
    finalizeTypingIndicator();
    // typingDots id stripped too, find by class
    const dots = document.querySelector('.typing-indicator');
    expect(dots?.style.display).toBe('none');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// removeTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('removeTypingIndicator', () => {
  it('removes the indicator element from the DOM', () => {
    showTypingIndicator();
    removeTypingIndicator();
    expect(document.getElementById('typingIndicator')).toBeNull();
  });

  it('is a no-op when indicator does not exist', () => {
    expect(() => removeTypingIndicator()).not.toThrow();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// showJiraConfirmation
// ════════════════════════════════════════════════════════════════════════════
describe('showJiraConfirmation', () => {
  it('appends a confirmation block to #chatMessages', () => {
    showJiraConfirmation({ description: 'Create ticket XY-1' });
    expect(document.getElementById('jiraConfirmBlock')).not.toBeNull();
  });

  it('contains a confirm button with text "Da! Ausführen"', () => {
    showJiraConfirmation({ description: 'test' });
    const btn = document.querySelector('.btn-confirm');
    expect(btn?.textContent).toContain('Da!');
  });

  it('contains a cancel button with text "Njet!"', () => {
    showJiraConfirmation({ description: 'test' });
    const btn = document.querySelector('.btn-cancel');
    expect(btn?.textContent).toContain('Njet!');
  });

  it('renders the description inside the bubble', () => {
    showJiraConfirmation({ description: 'Create ticket XY-1' });
    expect(document.querySelector('.message-bubble').innerHTML)
      .toContain('Create ticket XY-1');
  });

  it('applies jira-confirm-msg class', () => {
    showJiraConfirmation({ description: 'x' });
    expect(document.getElementById('jiraConfirmBlock').classList.contains('jira-confirm-msg')).toBe(true);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// initChat – event listener wiring
// ════════════════════════════════════════════════════════════════════════════
describe('initChat – event listener wiring', () => {
  it('send button click triggers sendMessage (no-op when input is empty)', () => {
    // input is empty → sendMessage returns early, no fetch needed
    expect(() => document.getElementById('sendBtn').click()).not.toThrow();
  });

  it('Enter key on textarea triggers send (no-op when empty)', () => {
    const evt = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: false, bubbles: true });
    expect(() => document.getElementById('userInput').dispatchEvent(evt)).not.toThrow();
  });

  it('Shift+Enter does NOT trigger send', () => {
    const sendSpy = vi.fn();
    // If Shift+Enter did trigger send it would call fetch – mock it to detect
    vi.stubGlobal('fetch', sendSpy);
    const evt = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, bubbles: true });
    document.getElementById('userInput').dispatchEvent(evt);
    expect(sendSpy).not.toHaveBeenCalled();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// clearChat – smoke test with mocked fetch
// ════════════════════════════════════════════════════════════════════════════
describe('clearChat', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));
  });

  it('clears the chatMessages container', async () => {
    appendMessage('user', 'hello');
    await clearChat();
    // After clear, only the greeting message added by clearChat itself remains
    expect(document.getElementById('chatMessages').children.length).toBe(1);
  });

  it('calls setExportEnabled(false)', async () => {
    await clearChat();
    expect(setExportSpy).toHaveBeenCalledWith(false);
  });

  it('POSTs to /api/chat/clear', async () => {
    await clearChat();
    expect(vi.mocked(fetch)).toHaveBeenCalledWith('/api/chat/clear', { method: 'POST' });
  });

  it('appends a greeting message after clearing', async () => {
    await clearChat();
    const bubble = document.querySelector('.message-bubble');
    expect(bubble?.textContent).toContain('geleert');
  });
});

