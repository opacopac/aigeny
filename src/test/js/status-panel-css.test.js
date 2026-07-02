/**
 * status-panel-css.test.js
 *
 * Verifies the structural integrity of status-panel.css and the extraction
 * contract (style.css must no longer own the moved rules).
 */
import { describe, it, expect, beforeAll } from "vitest";
import { readFileSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";
const __dirname  = dirname(fileURLToPath(import.meta.url));
const CSS_DIR    = resolve(__dirname, "../../main/resources/static/css");
const PANEL_CSS  = resolve(CSS_DIR, "status-panel.css");
const STYLE_CSS  = resolve(CSS_DIR, "style.css");
let panelCss;
let styleCss;
beforeAll(() => {
  panelCss = readFileSync(PANEL_CSS, "utf-8");
  styleCss = readFileSync(STYLE_CSS, "utf-8");
});
// ----------------------------------------------------------------------------
// status-panel.css � selector presence
// ----------------------------------------------------------------------------
describe("status-panel.css � selectors", () => {
  it("contains .info-box selector", () => {
    expect(panelCss).toMatch(/\.info-box\s*\{/);
  });
  it("contains .info-row selector", () => {
    expect(panelCss).toMatch(/\.info-row\s*\{/);
  });
  it("contains .info-key selector", () => {
    expect(panelCss).toMatch(/\.info-key\s*\{/);
  });
  it("contains .info-val selector", () => {
    expect(panelCss).toMatch(/\.info-val\s*\{/);
  });
  it("contains .info-val.ok selector", () => {
    expect(panelCss).toMatch(/\.info-val\.ok\s*\{/);
  });
  it("contains .info-val.error selector", () => {
    expect(panelCss).toMatch(/\.info-val\.error\s*\{/);
  });
  it("contains .info-val.warn selector", () => {
    expect(panelCss).toMatch(/\.info-val\.warn\s*\{/);
  });
  it("contains .btn-token selector", () => {
    expect(panelCss).toMatch(/\.btn-token\s*\{/);
  });
  it("contains .btn-token.visible selector", () => {
    expect(panelCss).toMatch(/\.btn-token\.visible\s*\{/);
  });
  it("contains .status-dot selector", () => {
    expect(panelCss).toMatch(/\.status-dot\s*\{/);
  });
  it("contains .status-dot.ok selector", () => {
    expect(panelCss).toMatch(/\.status-dot\.ok\s*\{/);
  });
  it("contains .status-dot.error selector", () => {
    expect(panelCss).toMatch(/\.status-dot\.error\s*\{/);
  });
  it("contains .status-dot.busy selector", () => {
    expect(panelCss).toMatch(/\.status-dot\.busy\s*\{/);
  });
  it("defines @keyframes pulse", () => {
    expect(panelCss).toMatch(/@keyframes\s+pulse\s*\{/);
  });
});
// ----------------------------------------------------------------------------
// status-panel.css � design tokens
// ----------------------------------------------------------------------------
describe("status-panel.css � design tokens", () => {
  it(".info-box uses var(--bg3) as background", () => {
    const block = panelCss.match(/\.info-box\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--bg3)");
  });
  it(".info-box uses var(--border) for border", () => {
    const block = panelCss.match(/\.info-box\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--border)");
  });
  it(".info-box uses var(--radius) for border-radius", () => {
    const block = panelCss.match(/\.info-box\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--radius)");
  });
  it(".info-key uses var(--text-dim) for color", () => {
    const block = panelCss.match(/\.info-key\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--text-dim)");
  });
  it(".info-val.error uses var(--red) for color", () => {
    const block = panelCss.match(/\.info-val\.error\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--red)");
  });
  it(".btn-token uses var(--red) for color", () => {
    const block = panelCss.match(/\.btn-token\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--red)");
  });
  it(".btn-token uses var(--red-dim) for border", () => {
    const block = panelCss.match(/\.btn-token\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toContain("var(--red-dim)");
  });
  it(".status-dot.error uses var(--red)", () => {
    expect(panelCss).toContain("var(--red)");
  });
});
// ----------------------------------------------------------------------------
// status-panel.css � layout values
// ----------------------------------------------------------------------------
describe("status-panel.css � layout values", () => {
  it(".info-box has width: 100%", () => {
    const block = panelCss.match(/\.info-box\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/width\s*:\s*100%/);
  });
  it(".info-row uses display: flex", () => {
    const block = panelCss.match(/\.info-row\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/display\s*:\s*flex/);
  });
  it(".status-dot has border-radius: 50%", () => {
    const block = panelCss.match(/\.status-dot\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/border-radius\s*:\s*50%/);
  });
  it(".status-dot.busy uses animation: pulse", () => {
    const block = panelCss.match(/\.status-dot\.busy\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/animation/);
    expect(block).toContain("pulse");
  });
  it(".btn-token is display: none by default", () => {
    const block = panelCss.match(/\.btn-token\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/display\s*:\s*none/);
  });
  it(".btn-token.visible overrides to display: inline-block", () => {
    const block = panelCss.match(/\.btn-token\.visible\s*\{([^}]+)\}/)?.[1] ?? "";
    expect(block).toMatch(/display\s*:\s*inline-block/);
  });
});
// ----------------------------------------------------------------------------
// Extraction contract � style.css must NOT contain moved rules
// ----------------------------------------------------------------------------
describe("extraction contract � style.css no longer owns status-panel rules", () => {
  it("style.css does not contain .info-box rule", () => {
    expect(styleCss).not.toMatch(/\.info-box\s*\{[^}]*background/);
  });
  it("style.css does not contain .info-row rule", () => {
    expect(styleCss).not.toMatch(/\.info-row\s*\{\s*display/);
  });
  it("style.css does not contain .info-val rule", () => {
    expect(styleCss).not.toMatch(/\.info-val\s*\{\s*color/);
  });
  it("style.css does not contain .btn-token rule", () => {
    expect(styleCss).not.toMatch(/\.btn-token\s*\{[^}]*display\s*:\s*none/);
  });
  it("style.css does not contain @keyframes pulse", () => {
    expect(styleCss).not.toMatch(/@keyframes\s+pulse/);
  });
  it("style.css does not contain .status-dot.ok rule", () => {
    expect(styleCss).not.toMatch(/\.status-dot\.ok\s*\{/);
  });
});
