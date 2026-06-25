/**
 * jira-write-mode-css.test.js
 *
 * Verifies the structural integrity of jira-write-mode.css:
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
const __dirname   = dirname(fileURLToPath(import.meta.url));
const CSS_DIR     = resolve(__dirname, '../../main/resources/static/css');
const JIRA_WRITE_CSS = resolve(CSS_DIR, 'jira-write-mode.css');
const STYLE_CSS      = resolve(CSS_DIR, 'style.css');

let jiraWriteCss;
let styleCss;

beforeAll(() => {
  jiraWriteCss = readFileSync(JIRA_WRITE_CSS, 'utf-8');
  styleCss     = readFileSync(STYLE_CSS,      'utf-8');
});

// ════════════════════════════════════════════════════════════════════════════
// write-mode.css – selector presence
// ════════════════════════════════════════════════════════════════════════════
describe('write-mode.css – selectors', () => {
  it('contains .write-mode-toggle selector', () => {
    expect(jiraWriteCss).toMatch(/\.write-mode-toggle\s*\{/);
  });

  it('contains .write-mode-label selector', () => {
    expect(jiraWriteCss).toMatch(/\.write-mode-label\s*\{/);
  });

  it('contains .toggle-switch selector', () => {
    expect(jiraWriteCss).toMatch(/\.toggle-switch\s*\{/);
  });

  it('contains .toggle-slider selector', () => {
    expect(jiraWriteCss).toMatch(/\.toggle-slider\s*\{/);
  });

  it('contains .toggle-slider::before pseudo-element', () => {
    expect(jiraWriteCss).toMatch(/\.toggle-slider::before\s*\{/);
  });

  it('contains checked-state rule for .toggle-slider (track)', () => {
    expect(jiraWriteCss).toMatch(/input:checked\s*\+\s*\.toggle-slider\s*\{/);
  });

  it('contains checked-state rule for .toggle-slider::before (knob)', () => {
    expect(jiraWriteCss).toMatch(/input:checked\s*\+\s*\.toggle-slider::before\s*\{/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// write-mode.css – design tokens
// ════════════════════════════════════════════════════════════════════════════
describe('write-mode.css – design tokens', () => {
  it('.write-mode-toggle uses var(--bg3) as background', () => {
    const block = jiraWriteCss.match(/\.write-mode-toggle\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--bg3)');
  });

  it('.write-mode-toggle uses var(--border) for border', () => {
    const block = jiraWriteCss.match(/\.write-mode-toggle\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--border)');
  });

  it('.write-mode-toggle uses var(--radius) for border-radius', () => {
    const block = jiraWriteCss.match(/\.write-mode-toggle\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--radius)');
  });

  it('.write-mode-label uses var(--text-dim) for color', () => {
    const block = jiraWriteCss.match(/\.write-mode-label\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toContain('var(--text-dim)');
  });

  it('checked track uses var(--red-dim) for border-color', () => {
    expect(jiraWriteCss).toContain('var(--red-dim)');
  });

  it('checked knob uses var(--red) as background', () => {
    expect(jiraWriteCss).toContain('var(--red)');
  });

  it('checked knob uses var(--red-glow) for box-shadow', () => {
    expect(jiraWriteCss).toContain('var(--red-glow)');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// write-mode.css – layout values
// ════════════════════════════════════════════════════════════════════════════
describe('write-mode.css – layout values', () => {
  it('.write-mode-toggle uses display: flex', () => {
    const block = jiraWriteCss.match(/\.write-mode-toggle\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/display\s*:\s*flex/);
  });

  it('.toggle-switch has width: 38px', () => {
    const block = jiraWriteCss.match(/\.toggle-switch\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/width\s*:\s*38px/);
  });

  it('.toggle-switch has height: 21px', () => {
    const block = jiraWriteCss.match(/\.toggle-switch\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/height\s*:\s*21px/);
  });

  it('.toggle-switch hides the native checkbox (opacity: 0)', () => {
    expect(jiraWriteCss).toMatch(/\.toggle-switch\s+input\s*\{[^}]*opacity\s*:\s*0/);
  });

  it('.toggle-slider is position: absolute', () => {
    const block = jiraWriteCss.match(/\.toggle-slider\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/position\s*:\s*absolute/);
  });

  it('checked knob translates exactly 17px', () => {
    expect(jiraWriteCss).toMatch(/translateX\(17px\)/);
  });

  it('.toggle-slider has a border-radius (pill shape)', () => {
    const block = jiraWriteCss.match(/\.toggle-slider\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(block).toMatch(/border-radius\s*:/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extraction contract – style.css must NOT contain moved rules
// ════════════════════════════════════════════════════════════════════════════
describe('extraction contract – style.css no longer owns write-mode rules', () => {
  it('style.css does not contain .write-mode-toggle', () => {
    expect(styleCss).not.toMatch(/\.write-mode-toggle\s*\{/);
  });

  it('style.css does not contain .write-mode-label', () => {
    expect(styleCss).not.toMatch(/\.write-mode-label\s*\{/);
  });

  it('style.css does not contain .toggle-switch', () => {
    expect(styleCss).not.toMatch(/\.toggle-switch\s*\{/);
  });

  it('style.css does not contain .toggle-slider', () => {
    expect(styleCss).not.toMatch(/\.toggle-slider\s*\{/);
  });
});

