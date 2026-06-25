/**
 * markdown-css.test.js
 *
 * Verifies the structural integrity of markdown.css and the extraction
 * contract (style.css must not contain any moved rules).
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname   = dirname(fileURLToPath(import.meta.url));
const CSS_DIR     = resolve(__dirname, '../../main/resources/static/css');
const MARKDOWN_CSS = resolve(CSS_DIR, 'markdown.css');
const STYLE_CSS   = resolve(CSS_DIR, 'style.css');

let mdCss;
let styleCss;

beforeAll(() => {
  mdCss    = readFileSync(MARKDOWN_CSS, 'utf-8');
  styleCss = readFileSync(STYLE_CSS,    'utf-8');
});

// ════════════════════════════════════════════════════════════════════════════
// markdown.css – selector presence
// ════════════════════════════════════════════════════════════════════════════
describe('markdown.css – selectors', () => {
  it('contains .message-bubble strong', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+strong/);
  });

  it('contains .message-bubble em', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+em/);
  });

  it('contains .message-bubble code', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+code/);
  });

  it('contains .message-bubble pre', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+pre\b/);
  });

  it('contains .message-bubble pre code (override for code inside pre)', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+pre\s+code/);
  });

  it('contains .message-bubble table', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+table/);
  });

  it('contains .message-bubble th', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+th/);
  });

  it('contains .message-bubble td', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+td\b/);
  });

  it('contains heading selectors (h1/h2/h3)', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+h[123]/);
  });

  it('contains list selectors (ul/ol)', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+(ul|ol)/);
  });

  it('contains .message-bubble li', () => {
    expect(mdCss).toMatch(/\.message-bubble\s+li/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// markdown.css – design token usage
// ════════════════════════════════════════════════════════════════════════════
describe('markdown.css – design tokens', () => {
  it('code uses var(--text-code) for color', () => {
    const codeBlock = mdCss.match(/\.message-bubble\s+code\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(codeBlock).toContain('var(--text-code)');
  });

  it('code uses var(--font-mono)', () => {
    const codeBlock = mdCss.match(/\.message-bubble\s+code\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(codeBlock).toContain('var(--font-mono)');
  });

  it('th uses var(--red-dim) as background', () => {
    const thBlock = mdCss.match(/\.message-bubble\s+th\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(thBlock).toContain('var(--red-dim)');
  });

  it('headings use var(--red) for color', () => {
    expect(mdCss).toContain('var(--red)');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// markdown.css – key property values
// ════════════════════════════════════════════════════════════════════════════
describe('markdown.css – key property values', () => {
  it('table uses border-collapse: collapse', () => {
    const tableBlock = mdCss.match(/\.message-bubble\s+table\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(tableBlock).toMatch(/border-collapse\s*:\s*collapse/);
  });

  it('pre code resets background to none', () => {
    const preCodeBlock = mdCss.match(/\.message-bubble\s+pre\s+code\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(preCodeBlock).toMatch(/background\s*:\s*none/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extraction contract – style.css must NOT contain moved rules
// ════════════════════════════════════════════════════════════════════════════
describe('extraction contract – style.css no longer owns markdown rules', () => {
  it('style.css does not contain .message-bubble strong', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+strong/);
  });

  it('style.css does not contain .message-bubble code', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+code/);
  });

  it('style.css does not contain .message-bubble pre', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+pre/);
  });

  it('style.css does not contain .message-bubble table', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+table/);
  });

  it('style.css does not contain .message-bubble th', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+th\b/);
  });

  it('style.css does not contain .message-bubble li', () => {
    expect(styleCss).not.toMatch(/\.message-bubble\s+li\b/);
  });
});

