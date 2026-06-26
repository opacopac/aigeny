/**
 * chat.test.js – Unit tests for the chat module
 *
 * The chat component is now split into three files:
 *   chat-renderer.js  – DOM creation / mutation (appendMessage, typing indicator, Jira confirm)
 *   chat-stream.js    – SSE fetch lifecycle      (sendMessage, stopGeneration)
 *   chat.js           – coordinator              (initChat, clearChat, exportData)
 *
 * All symbols are re-exported from chat.js so existing import paths remain valid.
 * Tests are grouped by the sub-module that owns the behaviour.
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
  showJiraBatchConfirmation,
  hasPendingJiraConfirmation,
  executeJiraAction,
  handleJiraConfirmAndResume,
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
// chat-renderer.js – appendMessage
// ════════════════════════════════════════════════════════════════════════════
describe('appendMessage (chat-renderer)', () => {
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
// chat-renderer.js – showTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('showTypingIndicator (chat-renderer)', () => {
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
// chat-renderer.js – updateTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('updateTypingIndicator (chat-renderer)', () => {
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
// chat-renderer.js – finalizeTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('finalizeTypingIndicator (chat-renderer)', () => {
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
// chat-renderer.js – removeTypingIndicator
// ════════════════════════════════════════════════════════════════════════════
describe('removeTypingIndicator (chat-renderer)', () => {
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
// chat-renderer.js – showJiraConfirmation
// ════════════════════════════════════════════════════════════════════════════
describe('showJiraConfirmation (chat-renderer)', () => {
  it('appends a confirmation block with .jira-confirm-msg class to #chatMessages', () => {
    showJiraConfirmation({ description: 'Create ticket XY-1' });
    expect(document.querySelector('.jira-confirm-msg')).not.toBeNull();
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
    showJiraConfirmation('Create ticket XY-1');
    expect(document.querySelector('.message-bubble').innerHTML)
      .toContain('Create ticket XY-1');
  });

  it('applies jira-confirm-msg class', () => {
    showJiraConfirmation({ description: 'x' });
    expect(document.querySelector('.jira-confirm-msg')).not.toBeNull();
  });

  it('does NOT set a fixed id on the confirmation block', () => {
    showJiraConfirmation({ description: 'x' });
    // Fixed ids cause duplicate-ID bugs when called multiple times; we use
    // the class selector instead.
    expect(document.getElementById('jiraConfirmBlock')).toBeNull();
  });

  it('multiple calls append multiple independent blocks', () => {
    showJiraConfirmation({ description: 'Action 1' });
    showJiraConfirmation({ description: 'Action 2' });
    expect(document.querySelectorAll('.jira-confirm-msg').length).toBe(2);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat-renderer.js – hasPendingJiraConfirmation
// ════════════════════════════════════════════════════════════════════════════
describe('hasPendingJiraConfirmation (chat-renderer)', () => {
  it('returns false when no confirmation block is present', () => {
    expect(hasPendingJiraConfirmation()).toBe(false);
  });

  it('returns true after showJiraConfirmation is called', () => {
    showJiraConfirmation({ description: 'action' });
    expect(hasPendingJiraConfirmation()).toBe(true);
  });

  it('returns false after the confirmation block is removed from DOM', () => {
    showJiraConfirmation({ description: 'action' });
    document.querySelector('.jira-confirm-msg').remove();
    expect(hasPendingJiraConfirmation()).toBe(false);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat.js (coordinator) – initChat event listener wiring
// ════════════════════════════════════════════════════════════════════════════
describe('initChat – event listener wiring (chat.js coordinator)', () => {
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
// chat.js (coordinator) – clearChat
// ════════════════════════════════════════════════════════════════════════════
describe('clearChat (chat.js coordinator)', () => {
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

// ════════════════════════════════════════════════════════════════════════════
// chat-stream.js – sendMessage smoke test (via chat.js re-export)
// ════════════════════════════════════════════════════════════════════════════
describe('sendMessage (chat-stream, via chat.js re-export)', () => {
  it('is a no-op when input is empty', async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    document.getElementById('userInput').value = '';
    await sendMessage();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('is a no-op when already thinking', async () => {
    // Re-init with isThinkingFn always returning true
    initChat({
      isThinkingFn:       () => true,
      setThinkingFn:      vi.fn(),
      setExportEnabledFn: vi.fn(),
    });
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    document.getElementById('userInput').value = 'hello';
    await sendMessage();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('does not call fetch when a Jira confirmation block is pending', async () => {
    // Simulate a pending confirmation block
    showJiraConfirmation({ description: 'Create ticket XY-1' });

    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    document.getElementById('userInput').value = 'hello';

    await sendMessage();

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('appends a warning message when blocked by a pending confirmation', async () => {
    showJiraConfirmation({ description: 'Create ticket XY-1' });
    vi.stubGlobal('fetch', vi.fn());
    document.getElementById('userInput').value = 'hello';

    await sendMessage();

    // At this point the DOM contains: the jira-confirm-msg + the warning
    const aigenyMessages = document.querySelectorAll('.message.aigeny');
    const warnText = [...aigenyMessages].map(el => el.textContent).join(' ');
    expect(warnText).toContain('ausstehende Jira-Aktion');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat-renderer.js – executeJiraAction (delegating to injected handler)
// ════════════════════════════════════════════════════════════════════════════
describe('executeJiraAction (chat-renderer – confirm-and-resume delegation)', () => {
  it('calls the injected confirm handler with the confirmation block', async () => {
    const handlerSpy = vi.fn().mockResolvedValue(undefined);
    // Wire the spy as the confirm handler (mimics what initChat does)
    initChat({
      isThinkingFn:       () => false,
      setThinkingFn:      vi.fn(),
      setExportEnabledFn: vi.fn(),
    });
    // Override with a spy by re-wiring via initChat (which calls setConfirmHandler internally)
    // We test the behaviour by calling executeJiraAction on a block and verifying
    // the /api/jira/confirm-decision fetch is attempted (the default wired handler).
    showJiraConfirmation({ description: 'Some action' });
    const block = document.querySelector('.jira-confirm-msg');

    // Stub fetch to avoid actual network call
    const fetchSpy = vi.fn().mockResolvedValue({
      body: {
        getReader: () => ({
          read: vi.fn().mockResolvedValue({ done: true }),
        }),
      },
    });
    vi.stubGlobal('fetch', fetchSpy);

    await executeJiraAction(block);

    // The confirm-decision endpoint must be called
    expect(fetchSpy).toHaveBeenCalledWith('/api/jira/confirm-decision', expect.objectContaining({ method: 'POST' }));
  });

  it('disables all buttons in the confirmation block before executing', async () => {
    showJiraConfirmation({ description: 'Some action' });
    const block = document.querySelector('.jira-confirm-msg');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      body: { getReader: () => ({ read: vi.fn().mockResolvedValue({ done: true }) }) },
    }));

    // Start (don't await so we can inspect mid-flight)
    executeJiraAction(block);

    // Buttons should be disabled immediately
    block.querySelectorAll('button').forEach(b => expect(b.disabled).toBe(true));
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat-stream.js – handleJiraConfirmAndResume
// ════════════════════════════════════════════════════════════════════════════
describe('handleJiraConfirmAndResume (chat-stream, via chat.js re-export)', () => {
  it('removes the confirmation block from DOM', async () => {
    showJiraConfirmation({ description: 'Some action' });
    const block = document.querySelector('.jira-confirm-msg');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      body: { getReader: () => ({ read: vi.fn().mockResolvedValue({ done: true }) }) },
    }));

    await handleJiraConfirmAndResume(block);

    expect(document.querySelector('.jira-confirm-msg')).toBeNull();
  });

  it('calls /api/jira/confirm-decision', async () => {
    showJiraConfirmation({ description: 'Some action' });
    const block = document.querySelector('.jira-confirm-msg');

    const fetchSpy = vi.fn().mockResolvedValue({
      body: { getReader: () => ({ read: vi.fn().mockResolvedValue({ done: true }) }) },
    });
    vi.stubGlobal('fetch', fetchSpy);

    await handleJiraConfirmAndResume(block);

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/jira/confirm-decision',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('shows a typing indicator during execution', () => {
    showJiraConfirmation({ description: 'Some action' });
    const block = document.querySelector('.jira-confirm-msg');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      body: { getReader: () => ({ read: vi.fn().mockResolvedValue({ done: true }) }) },
    }));

    // Don't await – inspect DOM during execution
    handleJiraConfirmAndResume(block);

    expect(document.getElementById('typingIndicator')).not.toBeNull();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat-renderer.js – showJiraBatchConfirmation
// ════════════════════════════════════════════════════════════════════════════
describe('showJiraBatchConfirmation (chat-renderer)', () => {
  const twoActions = [
    { toolCallId: 'call-1', toolName: 'create_jira_issue', description: 'Ticket erstellen in NOVA: Foo' },
    { toolCallId: 'call-2', toolName: 'add_jira_comment',  description: 'Kommentar zu NOVA-42' },
  ];

  it('appends a block with .jira-batch-confirm-msg to #chatMessages', () => {
    showJiraBatchConfirmation(twoActions);
    expect(document.querySelector('.jira-batch-confirm-msg')).not.toBeNull();
  });

  it('also has .jira-confirm-msg class (so hasPendingJiraConfirmation works)', () => {
    showJiraBatchConfirmation(twoActions);
    expect(document.querySelector('.jira-confirm-msg')).not.toBeNull();
  });

  it('hasPendingJiraConfirmation returns true after batch dialog is shown', () => {
    showJiraBatchConfirmation(twoActions);
    expect(hasPendingJiraConfirmation()).toBe(true);
  });

  it('renders one checkbox row per action', () => {
    showJiraBatchConfirmation(twoActions);
    const rows = document.querySelectorAll('.jira-batch-action-row');
    expect(rows.length).toBe(2);
  });

  it('all checkboxes start checked', () => {
    showJiraBatchConfirmation(twoActions);
    const checkboxes = document.querySelectorAll('.jira-batch-action-row input[type="checkbox"]');
    checkboxes.forEach(cb => expect(cb.checked).toBe(true));
  });

  it('contains a "Confirm All" button', () => {
    showJiraBatchConfirmation(twoActions);
    const btns = [...document.querySelectorAll('.jira-confirm-buttons button')];
    expect(btns.some(b => b.textContent.includes('Alle ausführen'))).toBe(true);
  });

  it('contains a "Decline All" button', () => {
    showJiraBatchConfirmation(twoActions);
    const btns = [...document.querySelectorAll('.jira-confirm-buttons button')];
    expect(btns.some(b => b.textContent.includes('Alle abbrechen'))).toBe(true);
  });

  it('contains a "Auswahl ausführen" button', () => {
    showJiraBatchConfirmation(twoActions);
    const btns = [...document.querySelectorAll('.jira-confirm-buttons button')];
    expect(btns.some(b => b.textContent.includes('Auswahl'))).toBe(true);
  });

  it('renders action descriptions inside the dialog', () => {
    showJiraBatchConfirmation(twoActions);
    const html = document.querySelector('.jira-batch-confirm-msg').innerHTML;
    expect(html).toContain('NOVA-42');
    expect(html).toContain('NOVA: Foo');
  });

  it('clicking "Confirm All" posts confirmAll:true to batch endpoint', async () => {
    showJiraBatchConfirmation(twoActions);
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchSpy);

    const confirmAllBtn = [...document.querySelectorAll('.jira-confirm-buttons button')]
      .find(b => b.textContent.includes('Alle ausführen'));
    await confirmAllBtn.onclick();

    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/jira/batch-confirm-decision',
      expect.objectContaining({ method: 'POST' })
    );
    const body = JSON.parse(fetchSpy.mock.calls[0][1].body);
    expect(body.confirmAll).toBe(true);
  });

  it('clicking "Decline All" posts confirmAll:false to batch endpoint', async () => {
    showJiraBatchConfirmation(twoActions);
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchSpy);

    const declineAllBtn = [...document.querySelectorAll('.jira-confirm-buttons button')]
      .find(b => b.textContent.includes('Alle abbrechen'));
    await declineAllBtn.onclick();

    const body = JSON.parse(fetchSpy.mock.calls[0][1].body);
    expect(body.confirmAll).toBe(false);
  });

  it('clicking "Auswahl" posts per-item decisions to batch endpoint', async () => {
    showJiraBatchConfirmation(twoActions);
    const fetchSpy = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchSpy);

    // Uncheck the second action
    const checkboxes = document.querySelectorAll('.jira-batch-action-row input[type="checkbox"]');
    checkboxes[1].checked = false;
    checkboxes[1].onchange();

    const auswählBtn = [...document.querySelectorAll('.jira-confirm-buttons button')]
      .find(b => b.textContent.includes('Auswahl'));
    await auswählBtn.onclick();

    const body = JSON.parse(fetchSpy.mock.calls[0][1].body);
    expect(body.decisions['call-1']).toBe(true);
    expect(body.decisions['call-2']).toBe(false);
  });

  it('removes the dialog after clicking any button', async () => {
    showJiraBatchConfirmation(twoActions);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));

    const confirmAllBtn = [...document.querySelectorAll('.jira-confirm-buttons button')]
      .find(b => b.textContent.includes('Alle ausführen'));
    await confirmAllBtn.onclick();

    expect(document.querySelector('.jira-batch-confirm-msg')).toBeNull();
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat-stream.js – batch_confirmation_required SSE handling
// ════════════════════════════════════════════════════════════════════════════
describe('batch_confirmation_required SSE event handling (chat-stream)', () => {
  it('sendMessage is blocked when a batch confirmation dialog is visible', async () => {
    // Show a batch confirmation dialog
    showJiraBatchConfirmation([
      { toolCallId: 'call-1', toolName: 'create_jira_issue', description: 'action 1' },
    ]);

    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    document.getElementById('userInput').value = 'hello';

    await sendMessage();

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('sendMessage appends a warning when blocked by batch confirmation', async () => {
    showJiraBatchConfirmation([
      { toolCallId: 'call-1', toolName: 'create_jira_issue', description: 'action 1' },
    ]);

    vi.stubGlobal('fetch', vi.fn());
    document.getElementById('userInput').value = 'hello';

    await sendMessage();

    const allMessages = [...document.querySelectorAll('.message.aigeny')].map(el => el.textContent);
    expect(allMessages.join(' ')).toContain('ausstehende Jira-Aktion');
  });
});

