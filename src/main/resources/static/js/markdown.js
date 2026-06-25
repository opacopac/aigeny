/**
 * markdown.js – Lightweight Markdown renderer
 *
 * Three pure functions with no DOM access and no side effects:
 *
 *  escapeHtml(s)           – HTML-encode the five unsafe characters
 *  convertTable(match)     – regex-replacer: Markdown table → <table> HTML
 *  renderMarkdown(text)    – full pipeline: escape → apply all rules → return HTML
 *
 * All three are exported so they can be tested and composed independently.
 */

/**
 * HTML-encode the five characters that are unsafe in HTML text content.
 * Must be applied before any Markdown substitution to prevent injection.
 * @param {string} s
 * @returns {string}
 */
export function escapeHtml(s) {
  return s
    .replace(/&/g,  '&amp;')
    .replace(/</g,  '&lt;')
    .replace(/>/g,  '&gt;')
    .replace(/"/g,  '&quot;')
    .replace(/'/g,  '&#39;');
}

/**
 * Convert a Markdown table block (multiple pipe-delimited lines) to HTML.
 * Used as a callback for String.replace().
 *
 * Rules:
 *  - First non-separator row becomes <th> cells.
 *  - Rows whose content is entirely whitespace / pipes / colons / dashes
 *    are treated as separator rows and skipped.
 *  - All other rows become <td> cells.
 *
 * @param {string} match – the full matched table text
 * @returns {string} – HTML <table> string, or the original match if < 2 rows
 */
export function convertTable(match) {
  const lines = match.trim().split('\n').filter(l => l.trim());
  if (lines.length < 2) return match;

  let table = '<table>';
  lines.forEach((line, i) => {
    if (line.replace(/[\s|:-]/g, '') === '') return; // separator row
    const cells = line
      .split('|')
      .filter((_, idx, arr) => idx > 0 && idx < arr.length - 1);
    if (i === 0) {
      table += '<tr>' + cells.map(c => `<th>${c.trim()}</th>`).join('') + '</tr>';
    } else {
      table += '<tr>' + cells.map(c => `<td>${c.trim()}</td>`).join('') + '</tr>';
    }
  });
  table += '</table>';
  return table;
}

/**
 * Render a subset of Markdown to safe HTML.
 * Supports: code blocks, inline code, bold, italic, h1–h3, tables,
 *           unordered lists, paragraphs, and line breaks.
 *
 * @param {string} text – raw Markdown input
 * @returns {string}    – HTML string (safe to set as innerHTML)
 */
export function renderMarkdown(text) {
  if (!text) return '';
  let html = escapeHtml(text);

  // Fenced code blocks  ``` … ```
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g,
    (_, _lang, code) => `<pre><code>${code.trim()}</code></pre>`);

  // Inline code  `…`
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Bold  **…**
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

  // Italic  *…*
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

  // Headings  #, ##, ###
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm,  '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm,   '<h1>$1</h1>');

  // Markdown tables  | col | col |
  html = html.replace(/((\|[^\n]+\|\n?)+)/g, convertTable);

  // Unordered lists  - item  /  * item
  html = html.replace(/^[-*] (.+)$/gm, '<li>$1</li>');
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');

  // Paragraphs (double newline)
  html = html.replace(/\n{2,}/g, '</p><p>');
  html = '<p>' + html + '</p>';

  // Single newlines → <br>
  html = html.replace(/\n/g, '<br>');

  // Clean up empty paragraphs
  html = html.replace(/<p>\s*<\/p>/g, '');

  return html;
}

