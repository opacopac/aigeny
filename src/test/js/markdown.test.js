import { describe, it, expect } from 'vitest';
import { escapeHtml, convertTable, renderMarkdown } from
  '../../main/resources/static/js/markdown.js';

// ════════════════════════════════════════════════════════════════════════════
// escapeHtml
// ════════════════════════════════════════════════════════════════════════════
describe('escapeHtml', () => {
  it('encodes ampersand', () => {
    expect(escapeHtml('a & b')).toBe('a &amp; b');
  });

  it('encodes less-than', () => {
    expect(escapeHtml('<div>')).toBe('&lt;div&gt;');
  });

  it('encodes greater-than', () => {
    expect(escapeHtml('a > b')).toBe('a &gt; b');
  });

  it('encodes double quotes', () => {
    expect(escapeHtml('"hello"')).toBe('&quot;hello&quot;');
  });

  it('encodes single quotes', () => {
    expect(escapeHtml("it's")).toBe('it&#39;s');
  });

  it('encodes all special characters together', () => {
    expect(escapeHtml('<a href="x&y">it\'s</a>'))
      .toBe('&lt;a href=&quot;x&amp;y&quot;&gt;it&#39;s&lt;/a&gt;');
  });

  it('leaves plain text unchanged', () => {
    expect(escapeHtml('hello world')).toBe('hello world');
  });

  it('returns empty string for empty input', () => {
    expect(escapeHtml('')).toBe('');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// convertTable
// ════════════════════════════════════════════════════════════════════════════
describe('convertTable', () => {
  it('returns the original text when fewer than 2 lines are present', () => {
    const single = '| only one row |';
    expect(convertTable(single)).toBe(single);
  });

  it('renders header row as <th> cells', () => {
    const md = '| Name | Age |\n|------|-----|\n| Alice | 30 |';
    const html = convertTable(md);
    expect(html).toContain('<th>Name</th>');
    expect(html).toContain('<th>Age</th>');
  });

  it('renders data rows as <td> cells', () => {
    const md = '| Name | Age |\n|------|-----|\n| Alice | 30 |';
    const html = convertTable(md);
    expect(html).toContain('<td>Alice</td>');
    expect(html).toContain('<td>30</td>');
  });

  it('skips separator rows (dashes / colons)', () => {
    const md = '| A | B |\n|---|---|\n| 1 | 2 |';
    const html = convertTable(md);
    // Separator must not appear as a row
    expect(html).not.toContain('<td>---</td>');
    expect(html).not.toContain('<th>---</th>');
  });

  it('trims whitespace from cell content', () => {
    const md = '|  Name  |  Age  |\n|--------|-------|\n|  Alice |  30   |';
    const html = convertTable(md);
    expect(html).toContain('<th>Name</th>');
    expect(html).toContain('<td>Alice</td>');
  });

  it('wraps everything in a <table> element', () => {
    const md = '| A |\n|---|\n| 1 |';
    expect(convertTable(md)).toMatch(/^<table>[\s\S]+<\/table>$/);
  });

  it('handles multiple data rows', () => {
    const md = '| X |\n|---|\n| 1 |\n| 2 |\n| 3 |';
    const html = convertTable(md);
    expect(html).toContain('<td>1</td>');
    expect(html).toContain('<td>2</td>');
    expect(html).toContain('<td>3</td>');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – safety & empty input
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – safety', () => {
  it('returns empty string for empty input', () => {
    expect(renderMarkdown('')).toBe('');
  });

  it('returns empty string for null', () => {
    expect(renderMarkdown(null)).toBe('');
  });

  it('returns empty string for undefined', () => {
    expect(renderMarkdown(undefined)).toBe('');
  });

  it('HTML-escapes angle brackets before applying Markdown', () => {
    const result = renderMarkdown('<script>alert(1)</script>');
    expect(result).not.toContain('<script>');
    expect(result).toContain('&lt;script&gt;');
  });

  it('HTML-escapes ampersands', () => {
    const result = renderMarkdown('a & b');
    expect(result).toContain('&amp;');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – inline formatting
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – inline formatting', () => {
  it('renders **bold** as <strong>', () => {
    expect(renderMarkdown('**hello**')).toContain('<strong>hello</strong>');
  });

  it('renders *italic* as <em>', () => {
    expect(renderMarkdown('*hello*')).toContain('<em>hello</em>');
  });

  it('renders `inline code` as <code>', () => {
    expect(renderMarkdown('use `x = 1`')).toContain('<code>x = 1</code>');
  });

  it('does not double-process bold inside italic delimiters', () => {
    // **bold** should not also match as two *italic* spans
    const result = renderMarkdown('**text**');
    expect(result).toContain('<strong>text</strong>');
    expect(result).not.toContain('<em>*text*</em>');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – code blocks
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – fenced code blocks', () => {
  it('renders a fenced code block as <pre><code>', () => {
    const md = '```\nconst x = 1;\n```';
    const result = renderMarkdown(md);
    expect(result).toContain('<pre><code>');
    expect(result).toContain('</code></pre>');
  });

  it('trims whitespace inside the code block', () => {
    const md = '```\n  trimmed  \n```';
    const result = renderMarkdown(md);
    expect(result).toContain('<code>trimmed</code>');
  });

  it('supports a language hint after the opening fence', () => {
    const md = '```js\nconst x = 1;\n```';
    expect(renderMarkdown(md)).toContain('<pre><code>');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – headings
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – headings', () => {
  it('renders # as <h1>', () => {
    expect(renderMarkdown('# Title')).toContain('<h1>Title</h1>');
  });

  it('renders ## as <h2>', () => {
    expect(renderMarkdown('## Section')).toContain('<h2>Section</h2>');
  });

  it('renders ### as <h3>', () => {
    expect(renderMarkdown('### Sub')).toContain('<h3>Sub</h3>');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – lists
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – unordered lists', () => {
  it('renders - items as <li> inside <ul>', () => {
    const result = renderMarkdown('- alpha\n- beta');
    expect(result).toContain('<ul>');
    expect(result).toContain('<li>alpha</li>');
    expect(result).toContain('<li>beta</li>');
  });

  it('does NOT support * as list delimiter (italic regex wins – known limitation)', () => {
    // The italic regex  /\*([^*]+)\*/  runs before the list regex and greedily
    // consumes "* one\n*", so "* item" syntax is not supported.
    // Use "- item" syntax instead.
    const result = renderMarkdown('* one\n* two');
    expect(result).not.toContain('<li>one</li>');
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – paragraphs & line breaks
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – paragraphs & line breaks', () => {
  it('wraps output in a paragraph', () => {
    const result = renderMarkdown('hello');
    expect(result).toMatch(/<p>.*hello.*<\/p>/s);
  });

  it('converts single newline to <br>', () => {
    const result = renderMarkdown('line one\nline two');
    expect(result).toContain('<br>');
  });

  it('converts double newline to paragraph break', () => {
    const result = renderMarkdown('para one\n\npara two');
    expect(result).toContain('</p><p>');
  });

  it('removes empty <p> tags', () => {
    const result = renderMarkdown('\n\n');
    expect(result).not.toMatch(/<p>\s*<\/p>/);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// renderMarkdown – tables (integration with convertTable)
// ════════════════════════════════════════════════════════════════════════════
describe('renderMarkdown – tables', () => {
  it('renders a Markdown table as an HTML table', () => {
    const md = '| Name | Score |\n|------|-------|\n| Alice | 99 |';
    const result = renderMarkdown(md);
    expect(result).toContain('<table>');
    expect(result).toContain('<th>Name</th>');
    expect(result).toContain('<td>Alice</td>');
  });
});


