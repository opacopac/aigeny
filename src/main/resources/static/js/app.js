/* ─────────────────────────────────────────────────────────────────────────
   AIgeny - frontend JavaScript
   - HAL 9000 eye animation (Canvas)
   - Chat send / receive
   - Export buttons
   - Status polling
   ───────────────────────────────────────────────────────────────────────── */

// ── State ──────────────────────────────────────────────────────────────────
let isThinking = false;
let hasExportData = false;

// ── HAL Eye ────────────────────────────────────────────────────────────────

function drawHalEye(canvasId, size, thinking) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const cx = size / 2, cy = size / 2;
  const outerR = size * 0.46;
  const midR   = size * 0.34;
  const innerR = size * 0.18;
  const pupilR = size * 0.09;

  ctx.clearRect(0, 0, size, size);

  // Outer dark ring
  ctx.beginPath();
  ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
  const outerGrad = ctx.createRadialGradient(cx, cy, midR, cx, cy, outerR);
  outerGrad.addColorStop(0, '#1a0000');
  outerGrad.addColorStop(1, '#050000');
  ctx.fillStyle = outerGrad;
  ctx.fill();
  ctx.strokeStyle = '#2a0000';
  ctx.lineWidth = 2;
  ctx.stroke();

  // Middle glowing red iris
  const glowIntensity = thinking ? (0.6 + 0.4 * Math.sin(Date.now() / 180)) : 0.85;
  ctx.beginPath();
  ctx.arc(cx, cy, midR, 0, Math.PI * 2);
  const irisGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, midR);
  irisGrad.addColorStop(0, `rgba(255, 40, 0, ${glowIntensity})`);
  irisGrad.addColorStop(0.4, `rgba(200, 0, 0, ${glowIntensity * 0.9})`);
  irisGrad.addColorStop(0.8, `rgba(120, 0, 0, ${glowIntensity * 0.7})`);
  irisGrad.addColorStop(1, `rgba(60, 0, 0, ${glowIntensity * 0.5})`);
  ctx.fillStyle = irisGrad;
  ctx.fill();

  // Red glow around iris when thinking
  if (thinking) {
    ctx.beginPath();
    ctx.arc(cx, cy, midR + 4, 0, Math.PI * 2);
    const glow = ctx.createRadialGradient(cx, cy, midR, cx, cy, midR + size * 0.12);
    glow.addColorStop(0, `rgba(220, 0, 0, ${0.3 * glowIntensity})`);
    glow.addColorStop(1, 'rgba(220, 0, 0, 0)');
    ctx.fillStyle = glow;
    ctx.fill();
  }

  // Inner lens ring
  ctx.beginPath();
  ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
  const lensGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, innerR);
  lensGrad.addColorStop(0, '#ff4422');
  lensGrad.addColorStop(0.5, '#cc1100');
  lensGrad.addColorStop(1, '#880000');
  ctx.fillStyle = lensGrad;
  ctx.fill();
  ctx.strokeStyle = `rgba(255, 80, 50, ${glowIntensity * 0.6})`;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // Pupil
  ctx.beginPath();
  ctx.arc(cx, cy, pupilR, 0, Math.PI * 2);
  ctx.fillStyle = '#050000';
  ctx.fill();

  // Specular highlight
  ctx.beginPath();
  ctx.arc(cx - innerR * 0.3, cy - innerR * 0.3, pupilR * 0.35, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(255, 180, 150, ${0.4 * glowIntensity})`;
  ctx.fill();
}

// Mini HAL eye in header (32x32)
function drawHalEyeMini(thinking) {
  drawHalEye('halEyeMini', 32, thinking);
}

// Main HAL eye (220x220)
let halAnimFrame;
function animateHalEye() {
  drawHalEye('halEye', 220, isThinking);
  drawHalEyeMini(isThinking);
  halAnimFrame = requestAnimationFrame(animateHalEye);
}
animateHalEye();

// ── Chat rendering ─────────────────────────────────────────────────────────

function appendMessage(role, text) {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.className = 'message ' + role;

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot = document.createElement('span');
  dot.className = 'dot';
  const name = document.createElement('span');
  name.textContent = role === 'user' ? 'Du' : 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  bubble.innerHTML = renderMarkdown(text);

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
  return div;
}

function showTypingIndicator() {
  const container = document.getElementById('chatMessages');
  const div = document.createElement('div');
  div.id = 'typingIndicator';
  div.className = 'message aigeny';

  const header = document.createElement('div');
  header.className = 'message-header';
  const dot = document.createElement('span'); dot.className = 'dot';
  const name = document.createElement('span'); name.textContent = 'AIgeny';
  header.append(dot, name);

  const bubble = document.createElement('div');
  bubble.className = 'message-bubble';
  bubble.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';

  div.append(header, bubble);
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

function removeTypingIndicator() {
  const el = document.getElementById('typingIndicator');
  if (el) el.remove();
}

// Very lightweight Markdown renderer (no external deps)
function renderMarkdown(text) {
  if (!text) return '';
  let html = escapeHtml(text);

  // Code blocks (``` ... ```)
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, (_, lang, code) =>
    `<pre><code>${code.trim()}</code></pre>`);

  // Inline code
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');

  // Bold **text**
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');

  // Italic *text*
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');

  // Headers
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm,  '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm,   '<h1>$1</h1>');

  // Markdown tables  | col | col |
  html = html.replace(/((\|[^\n]+\|\n?)+)/g, convertTable);

  // Unordered list
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

function convertTable(match) {
  const lines = match.trim().split('\n').filter(l => l.trim());
  if (lines.length < 2) return match;

  let table = '<table>';
  lines.forEach((line, i) => {
    if (line.replace(/[\s|:-]/g, '') === '') return; // separator row
    const cells = line.split('|').filter((_, idx, arr) => idx > 0 && idx < arr.length - 1);
    if (i === 0) {
      table += '<tr>' + cells.map(c => `<th>${c.trim()}</th>`).join('') + '</tr>';
    } else {
      table += '<tr>' + cells.map(c => `<td>${c.trim()}</td>`).join('') + '</tr>';
    }
  });
  table += '</table>';
  return table;
}

function escapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ── Send message ───────────────────────────────────────────────────────────

async function sendMessage() {
  if (isThinking) return;
  const input = document.getElementById('userInput');
  const message = input.value.trim();
  if (!message) return;

  input.value = '';
  appendMessage('user', message);
  setThinking(true);
  showTypingIndicator();

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message })
    });
    const data = await res.json();
    removeTypingIndicator();

    if (data.response) {
      appendMessage('aigeny', data.response);
    }

    if (data.hasExport) {
      hasExportData = true;
      setExportButtons(true);
    }
  } catch (err) {
    removeTypingIndicator();
    appendMessage('aigeny', 'Njet! Netzwerkfehler, Towarischtsch: ' + err.message);
  } finally {
    setThinking(false);
  }
}

// Enter = send, Shift+Enter = new line
document.getElementById('userInput').addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

// ── Controls ───────────────────────────────────────────────────────────────

async function clearChat() {
  await fetch('/api/chat/clear', { method: 'POST' });
  document.getElementById('chatMessages').innerHTML = '';
  hasExportData = false;
  setExportButtons(false);
  // Greeting
  appendMessage('aigeny',
    'Da, Chat wurde geleert, Towarischtsch! AIgeny ist bereit für neue Fragen. ' +
    'Was möchtest du heute aus Datenbank wissen?');
}

async function reloadSchema() {
  const btn = event.target;
  btn.disabled = true;
    btn.textContent = '⌛ Lade...';
  try {
    const res = await fetch('/api/schema/reload', { method: 'POST' });
    const data = await res.json();
    if (data.status === 'ok') {
      document.getElementById('infoTables').textContent = data.tables;
      appendMessage('aigeny',
        `Da! Schema neu geladen, Towarischtsch. Ich kenne jetzt **${data.tables}** Tabellen in Datenbank. Otschen choroscho!`);
    } else {
      appendMessage('aigeny', 'Njet! Schema-Neuladen fehlgeschlagen: ' + (data.message || 'unbekannter Fehler'));
    }
  } catch (err) {
    appendMessage('aigeny', 'Njet! Schema konnte nicht geladen werden: ' + err.message);
  } finally {
    btn.disabled = false;
    btn.textContent = '↻ Schema neu laden';
  }
}

function exportData(format) {
  window.location.href = '/api/export/' + format;
}

// ── State helpers ──────────────────────────────────────────────────────────

function setThinking(thinking) {
  isThinking = thinking;
  document.getElementById('sendBtn').disabled = thinking;
  document.getElementById('halStatusText').textContent =
    thinking ? 'AIgeny mysleet... (denkt nach)' : 'Bereit, Towarischtsch.';
  setStatusIndicator(thinking ? 'busy' : 'ok', thinking ? 'Denkt nach...' : 'Bereit');
}

function setExportButtons(enabled) {
  document.getElementById('btnCsv').disabled = !enabled;
}

function setStatusIndicator(type, text) {
  const dot  = document.getElementById('statusDot');
  const span = document.getElementById('statusText');
  dot.className  = 'status-dot ' + type;
  span.textContent = text;
}

// ── Status polling ─────────────────────────────────────────────────────────

async function loadStatus() {
  try {
    const res  = await fetch('/api/status');
    const data = await res.json();

    document.getElementById('infoLlm').textContent   = data.llmProvider  || '—';
    document.getElementById('infoModel').textContent = data.llmModel     || '—';
    document.getElementById('infoTables').textContent= data.schemaTables || '0';

    const dbEl = document.getElementById('infoDb');
    dbEl.textContent  = data.dbConfigured ? 'Connected' : 'Not configured';
    dbEl.className    = 'info-val ' + (data.dbConfigured ? 'ok' : 'error');

    const jiraEl = document.getElementById('infoJira');
    jiraEl.textContent = data.jiraConfigured ? 'Connected' : 'Not configured';
    jiraEl.className   = 'info-val ' + (data.jiraConfigured ? 'ok' : 'error');

    if (data.hasExport) {
      hasExportData = true;
      setExportButtons(true);
    }

    if (!isThinking) setStatusIndicator('ok', 'Ready');
  } catch {
    if (!isThinking) setStatusIndicator('error', 'Server nicht erreichbar');
  }
}

// ── Init ───────────────────────────────────────────────────────────────────

window.addEventListener('load', () => {
  loadStatus();
  setInterval(loadStatus, 15000);

  // Welcome message
  appendMessage('aigeny',
    'Privet Towarischtsch! Ich bin AIgeny - dein persönlicher Daten-Assistent mit russische Seele.\n\n' +
    'Du kannst mir Fragen zur Datenbank stellen, Berichte anfordern oder Jira-Tickets suchen.\n\n' +
    '**Was kann ich heute für dich tun?**');
});

