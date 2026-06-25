/**
 * modal-css.test.js
 *
 * Verifies the structural integrity of modal.css:
 *  - All expected selectors are present.
 *  - Key design tokens (CSS custom properties) are referenced correctly.
 *  - Key layout / sizing values are in place.
 *
 * Also guards the extraction contract:
 *  - style.css must NOT contain any of the moved rules.
 *
 * Approach: plain-text / regex assertions on the raw file content.
 * No CSS parser needed – the goal is to catch accidental deletions or
 * mis-merges, not to validate CSS syntax.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

// ── File paths ────────────────────────────────────────────────────────────────
const __dirname  = dirname(fileURLToPath(import.meta.url));
const CSS_DIR    = resolve(__dirname, '../../main/resources/static/css');
const MODAL_CSS  = resolve(CSS_DIR, 'modal.css');
const STYLE_CSS  = resolve(CSS_DIR, 'style.css');

let modalCss;
let styleCss;

beforeAll(() => {
  modalCss = readFileSync(MODAL_CSS, 'utf-8');
  styleCss = readFileSync(STYLE_CSS, 'utf-8');
});

// ════════════════════════════════════════════════════════════════════════════
// modal.css – selector presence
// ════════════════════════════════════════════════════════════════════════════
describe('modal.css – selectors', () => {
  it('contains .modal-overlay selector', () => {
    expect(modalCss).toMatch(/\.modal-overlay\s*\{/);
  });

  it('contains .modal-box selector', () => {
    expect(modalCss).toMatch(/\.modal-box\s*\{/);
  });

  it('contains .modal-box h2 selector', () => {
    expect(modalCss).toMatch(/\.modal-box\s+h2\s*\{/);
  });

  it('contains .modal-desc selector', () => {
    expect(modalCss).toMatch(/\.modal-desc\s*\{/);
  });

  it('contains .modal-input selector', () => {
    expect(modalCss).toMatch(/\.modal-input\s*\{/);
  });

  it('contains .modal-input:focus selector', () => {
    expect(modalCss).toMatch(/\.modal-input:focus\s*\{/);
  });

  it('contains .modal-hint selector', () => {
    expect(modalCss).toMatch(/\.modal-hint\s*\{/);
  });

  it('contains .modal-hint.ok selector', () => {
    expect(modalCss).toMatch(/\.modal-hint\.ok\s*\{/);
  });

  it('contains .modal-hint.error selector', () => {
    expect(modalCss).toMatch(/\.modal-hint\.error\s*\{/);
  });

  it('contains .modal-actions selector', () => {
    expect(modalCss).toMatch(/\.modal-actions\s*\{/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// modal.css – design tokens
// ════════════════════════════════════════════════════════════════════════════
describe('modal.css – design tokens', () => {
  it('.modal-overlay uses rgba backdrop (not a token – intentional)', () => {
    const block = modalCss.match(/\.modal-overlay\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/background\s*:/);
  });

  it('.modal-box uses var(--bg2) as background', () => {
    const block = modalCss.match(/\.modal-box\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--bg2)');
  });

  it('.modal-box uses var(--border) for border', () => {
    const block = modalCss.match(/\.modal-box\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--border)');
  });

  it('.modal-box uses var(--radius) for border-radius', () => {
    const block = modalCss.match(/\.modal-box\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--radius)');
  });

  it('.modal-box h2 uses var(--red) for color', () => {
    const block = modalCss.match(/\.modal-box\s+h2\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--red)');
  });

  it('.modal-desc uses var(--text-dim) for color', () => {
    const block = modalCss.match(/\.modal-desc\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--text-dim)');
  });

  it('.modal-input uses var(--bg3) as background', () => {
    const block = modalCss.match(/\.modal-input\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--bg3)');
  });

  it('.modal-input uses var(--font-mono) for font-family', () => {
    const block = modalCss.match(/\.modal-input\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--font-mono)');
  });

  it('.modal-input:focus uses var(--red-dim) for border-color', () => {
    const block = modalCss.match(/\.modal-input:focus\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--red-dim)');
  });

  it('.modal-hint.error uses var(--red) for color', () => {
    const block = modalCss.match(/\.modal-hint\.error\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--red)');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// modal.css – layout values
// ════════════════════════════════════════════════════════════════════════════
describe('modal.css – layout values', () => {
  it('.modal-overlay is position: fixed', () => {
    const block = modalCss.match(/\.modal-overlay\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/position\s*:\s*fixed/);
  });

  it('.modal-overlay has z-index: 1000', () => {
    const block = modalCss.match(/\.modal-overlay\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/z-index\s*:\s*1000/);
  });

  it('.modal-box has max-width: 440px', () => {
    const block = modalCss.match(/\.modal-box\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/max-width\s*:\s*440px/);
  });

  it('.modal-hint has a min-height', () => {
    const block = modalCss.match(/\.modal-hint\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/min-height\s*:/);
  });

  it('.modal-actions uses display: flex', () => {
    const block = modalCss.match(/\.modal-actions\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/display\s*:\s*flex/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extraction contract – style.css must NOT contain moved rules
// ════════════════════════════════════════════════════════════════════════════
describe('extraction contract – style.css no longer owns modal rules', () => {
  it('style.css does not contain .modal-overlay', () => {
    expect(styleCss).not.toMatch(/\.modal-overlay\s*\{/);
  });

  it('style.css does not contain .modal-box', () => {
    expect(styleCss).not.toMatch(/\.modal-box\s*\{/);
  });

  it('style.css does not contain .modal-input', () => {
    expect(styleCss).not.toMatch(/\.modal-input\s*\{/);
  });

  it('style.css does not contain .modal-hint', () => {
    expect(styleCss).not.toMatch(/\.modal-hint\s*\{/);
  });

  it('style.css does not contain .modal-actions', () => {
    expect(styleCss).not.toMatch(/\.modal-actions\s*\{/);
  });
});

