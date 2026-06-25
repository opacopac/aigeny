/**
 * hal-eye-css.test.js
 *
 * Verifies the structural integrity of hal-eye.css:
 *  - All expected selectors are present.
 *  - Key design tokens (CSS custom properties) are referenced correctly.
 *  - The responsive hide-rule is in place.
 *
 * Also guards the extraction contract:
 *  - style.css must NOT contain any of the moved rules.
 *
 * Approach: we read the files as plain text and use string/regex assertions.
 * No CSS parser dependency is needed at this level; the goal is to catch
 * accidental deletions or mis-merges, not to validate CSS syntax.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

// ── File paths ────────────────────────────────────────────────────────────────
const __dirname  = dirname(fileURLToPath(import.meta.url));
const CSS_DIR    = resolve(__dirname, '../../main/resources/static/css');
const HAL_CSS    = resolve(CSS_DIR, 'hal-eye.css');
const STYLE_CSS  = resolve(CSS_DIR, 'style.css');

let halCss;
let styleCss;

beforeAll(() => {
  halCss   = readFileSync(HAL_CSS,   'utf-8');
  styleCss = readFileSync(STYLE_CSS, 'utf-8');
});

// ════════════════════════════════════════════════════════════════════════════
// hal-eye.css – selector presence
// ════════════════════════════════════════════════════════════════════════════
describe('hal-eye.css – selectors', () => {
  it('contains .hal-panel selector', () => {
    expect(halCss).toMatch(/\.hal-panel\s*\{/);
  });

  it('contains #halEye selector', () => {
    expect(halCss).toMatch(/#halEye\s*\{/);
  });

  it('contains .hal-label selector', () => {
    expect(halCss).toMatch(/\.hal-label\s*\{/);
  });

  it('contains .hal-status selector', () => {
    expect(halCss).toMatch(/\.hal-status\s*\{/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// hal-eye.css – key design-token usage
// ════════════════════════════════════════════════════════════════════════════
describe('hal-eye.css – design tokens', () => {
  it('.hal-panel uses var(--bg2) as background', () => {
    // Locate the .hal-panel block and verify the token
    const panelBlock = halCss.match(/\.hal-panel\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(panelBlock).toContain('var(--bg2)');
  });

  it('.hal-panel uses var(--border) for its right border', () => {
    const panelBlock = halCss.match(/\.hal-panel\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(panelBlock).toContain('var(--border)');
  });

  it('.hal-label uses var(--red) for color', () => {
    const labelBlock = halCss.match(/\.hal-label\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(labelBlock).toContain('var(--red)');
  });

  it('.hal-label uses var(--red-glow) for text-shadow', () => {
    const labelBlock = halCss.match(/\.hal-label\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(labelBlock).toContain('var(--red-glow)');
  });

  it('.hal-status uses var(--text-dim) for color', () => {
    const statusBlock = halCss.match(/\.hal-status\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(statusBlock).toContain('var(--text-dim)');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// hal-eye.css – layout values
// ════════════════════════════════════════════════════════════════════════════
describe('hal-eye.css – layout values', () => {
  it('.hal-panel has width: 310px', () => {
    const panelBlock = halCss.match(/\.hal-panel\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(panelBlock).toMatch(/width\s*:\s*310px/);
  });

  it('.hal-status has font-style: italic', () => {
    const statusBlock = halCss.match(/\.hal-status\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(statusBlock).toMatch(/font-style\s*:\s*italic/);
  });

  it('.hal-status has a min-height', () => {
    const statusBlock = halCss.match(/\.hal-status\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(statusBlock).toMatch(/min-height\s*:/);
  });

  it('#halEye has display: block', () => {
    const eyeBlock = halCss.match(/#halEye\s*\{([^}]+)\}/)?.[1] ?? '';
    expect(eyeBlock).toMatch(/display\s*:\s*block/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// hal-eye.css – responsive rule
// ════════════════════════════════════════════════════════════════════════════
describe('hal-eye.css – responsive', () => {
  it('contains a @media rule for small screens', () => {
    expect(halCss).toMatch(/@media\s*\(max-width\s*:\s*700px\)/);
  });

  it('hides .hal-panel on small screens', () => {
    // Find the media block and check it contains the hide rule
    const mediaBlock = halCss.match(/@media[^{]+\{([\s\S]*?)\}/)?.[1] ?? '';
    expect(mediaBlock).toMatch(/\.hal-panel\s*\{[^}]*display\s*:\s*none/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extraction contract – style.css must NOT contain moved rules
// ════════════════════════════════════════════════════════════════════════════
describe('extraction contract – style.css no longer owns HAL rules', () => {
  it('style.css does not contain .hal-panel', () => {
    expect(styleCss).not.toMatch(/\.hal-panel\s*\{/);
  });

  it('style.css does not contain .hal-label', () => {
    expect(styleCss).not.toMatch(/\.hal-label\s*\{/);
  });

  it('style.css does not contain .hal-status', () => {
    expect(styleCss).not.toMatch(/\.hal-status\s*\{/);
  });

  it('style.css does not contain #halEye', () => {
    expect(styleCss).not.toMatch(/#halEye/);
  });
});

