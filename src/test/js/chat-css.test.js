/**
 * chat-css.test.js
 *
 * Verifies the structural integrity of chat.css and the extraction contract
 * (style.css must not contain any of the moved rules).
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname  = dirname(fileURLToPath(import.meta.url));
const CSS_DIR    = resolve(__dirname, '../../main/resources/static/css');
const CHAT_CSS   = resolve(CSS_DIR, 'chat.css');
const STYLE_CSS  = resolve(CSS_DIR, 'style.css');

let chatCss;
let styleCss;

beforeAll(() => {
  chatCss  = readFileSync(CHAT_CSS,  'utf-8');
  styleCss = readFileSync(STYLE_CSS, 'utf-8');
});

// ════════════════════════════════════════════════════════════════════════════
// chat.css – selector presence
// ════════════════════════════════════════════════════════════════════════════
describe('chat.css – selectors', () => {
  it('contains .chat-panel',    () => expect(chatCss).toMatch(/\.chat-panel\s*\{/));
  it('contains .chat-messages', () => expect(chatCss).toMatch(/\.chat-messages\s*\{/));
  it('contains .message',       () => expect(chatCss).toMatch(/\.message\s*\{/));
  it('contains .message-header',() => expect(chatCss).toMatch(/\.message-header\s*\{/));
  it('contains .message-bubble',() => expect(chatCss).toMatch(/\.message-bubble\s*\{/));
  it('contains .typing-indicator', () => expect(chatCss).toMatch(/\.typing-indicator\s*\{/));
  it('contains .tool-call-list',() => expect(chatCss).toMatch(/\.tool-call-list\s*\{/));
  it('contains .tool-call-item',() => expect(chatCss).toMatch(/\.tool-call-item\s*\{/));
  it('contains .input-area',    () => expect(chatCss).toMatch(/\.input-area\s*\{/));
  it('contains #userInput',     () => expect(chatCss).toMatch(/#userInput\s*\{/));
  it('contains .btn-send',      () => expect(chatCss).toMatch(/\.btn-send\s*\{/));
  it('contains .btn-stop',      () => expect(chatCss).toMatch(/\.btn-stop\s*\{/));
  it('contains .action-bar',    () => expect(chatCss).toMatch(/\.action-bar\s*\{/));
  it('contains .btn-export',    () => expect(chatCss).toMatch(/\.btn-export\s*\{/));
  it('contains .jira-confirm-msg', () => expect(chatCss).toMatch(/\.jira-confirm-msg/));
  it('contains .btn-confirm',   () => expect(chatCss).toMatch(/\.btn-confirm\s*\{/));
  it('contains .btn-cancel',    () => expect(chatCss).toMatch(/\.btn-cancel\s*\{/));
});

// ════════════════════════════════════════════════════════════════════════════
// chat.css – key design-token usage
// ════════════════════════════════════════════════════════════════════════════
describe('chat.css – design tokens', () => {
  it('.btn-send uses var(--red) as background', () => {
    const block = chatCss.match(/\.btn-send\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--red)');
  });

  it('.typing-indicator span uses var(--red) color', () => {
    expect(chatCss).toContain('var(--red)');
  });

  it('.message.aigeny .dot uses var(--red)', () => {
    expect(chatCss).toMatch(/\.message\.aigeny\s+\.dot[^}]*var\(--red\)/s);
  });

  it('.input-area uses var(--bg2) background', () => {
    const block = chatCss.match(/\.input-area\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--bg2)');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat.css – animations
// ════════════════════════════════════════════════════════════════════════════
describe('chat.css – animations', () => {
  it('defines @keyframes fadeIn', () => {
    expect(chatCss).toMatch(/@keyframes\s+fadeIn/);
  });

  it('defines @keyframes bounce (for typing dots)', () => {
    expect(chatCss).toMatch(/@keyframes\s+bounce/);
  });

  it('defines @keyframes pulse-stop (for stop button)', () => {
    expect(chatCss).toMatch(/@keyframes\s+pulse-stop/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// chat.css – responsive rule
// ════════════════════════════════════════════════════════════════════════════
describe('chat.css – responsive', () => {
  it('contains a @media max-width 700px rule', () => {
    expect(chatCss).toMatch(/@media\s*\(max-width\s*:\s*700px\)/);
  });

  it('limits message max-width on small screens', () => {
    const mediaBlock = chatCss.match(/@media[^{]+\{([\s\S]*?)\}/)?.[1] ?? '';
    expect(mediaBlock).toMatch(/\.message\s*\{[^}]*max-width/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extraction contract – style.css must NOT contain moved rules
// ════════════════════════════════════════════════════════════════════════════
describe('extraction contract – style.css no longer owns chat rules', () => {
  it('style.css does not contain .chat-panel',     () => expect(styleCss).not.toMatch(/\.chat-panel\s*\{/));
  it('style.css does not contain .chat-messages',  () => expect(styleCss).not.toMatch(/\.chat-messages\s*\{/));
  it('style.css does not contain .message-bubble', () => expect(styleCss).not.toMatch(/\.message-bubble\s*\{/));
  it('style.css does not contain .typing-indicator', () => expect(styleCss).not.toMatch(/\.typing-indicator\s*\{/));
  it('style.css does not contain .input-area',     () => expect(styleCss).not.toMatch(/\.input-area\s*\{/));
  it('style.css does not contain .btn-send',       () => expect(styleCss).not.toMatch(/\.btn-send\s*\{/));
  it('style.css does not contain .btn-stop',       () => expect(styleCss).not.toMatch(/\.btn-stop\s*\{/));
  it('style.css does not contain .btn-confirm',    () => expect(styleCss).not.toMatch(/\.btn-confirm\s*\{/));
  it('style.css does not contain .btn-cancel',     () => expect(styleCss).not.toMatch(/\.btn-cancel\s*\{/));
  it('style.css does not contain .action-bar',     () => expect(styleCss).not.toMatch(/\.action-bar\s*\{/));
});

