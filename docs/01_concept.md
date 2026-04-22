# AIgeny — Concept & Requirements

> **AIgeny** is a local Java desktop AI assistant for data managers, named after *Evgeny* —
> a Russian colleague who traditionally helps data managers with database requests.
> The system takes over that role as an AI agent with a Russian accent and HAL 9000 aesthetics.

---

## 1. Goal & Purpose

Data managers should be able to ask questions about an Oracle database in natural language
(English or German) without needing any SQL knowledge. The AI agent translates questions
into SQL queries, executes them and presents the results — in the style of "AIgeny" with a Russian accent.

### Typical Use Cases
- "Show me all open orders from the last 30 days"
- "How many records does table X have with status = active?"
- "Create a report of orders per region and export it as Excel"
- "Search all Jira tickets in project NOVA with status Open"

---

## 2. Core Features

| # | Feature | Description |
|---|---|---|
| F1 | **Natural-language DB queries** | User asks a question → LLM generates SQL → result is displayed |
| F2 | **Jira integration** | Search tickets via JQL through the Jira REST API |
| F3 | **Export** | Tabular results downloadable as CSV or Excel (.xlsx) |
| F4 | **DB schema loading** | Oracle schema loaded automatically on startup for LLM context |
| F5 | **SQL confirmation** | Every LLM-generated SQL query must be confirmed by the user |
| F6 | **AIgeny persona** | The agent replies in Russian-accented English at all times |
| F7 | **LLM provider switching** | Easy switch between Groq, OpenAI, Azure, Ollama via config |
| F8 | **Docker deployment** | App runs in Docker, browser access via noVNC |

---

## 3. Non-Functional Requirements

- **Security**: Only SELECT queries allowed; passwords AES-256 encrypted at rest
- **Privacy**: Ollama option for fully local operation (no data sent externally)
- **Portability**: Docker-based, runs on Windows / Mac / Linux
- **Extensibility**: LLM provider is swappable; additional tools can be added
- **Usability**: First-run wizard, no manual config file editing required

---

## 4. User Interface (Concept)

```
┌─────────────────────────────────────────────────────────────────┐
│ 🔴  AIgeny              AI Data Assistant | v1.0   [⚙ Settings] │
├──────────┬──────────────────────────────────────────────────────┤
│          │                                                      │
│  HAL Eye │  🔴 AIgeny:                                          │
│  (red    │  Da, privet comrade! I am AIgeny — your Russian      │
│  pulsing │  data expert. Ask me about ze database, da?          │
│  glow    │                                                      │
│  anima-  │  👤 You:                                             │
│  tion)   │  Show me all orders from the last 7 days             │
│          │                                                      │
│          │  🔴 AIgeny:                                          │
│          │  Horosho! I have queried ze database, comrade...     │
│          │  [table with results]                                │
│          │  You can export zis as CSV or Excel, da!            │
├──────────┴──────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────┐  [Send ▶]          │
│ │ Type your question...                    │                    │
│ └──────────────────────────────────────────┘                    │
│  [⬇ CSV]  [⬇ Excel]   [🗑 Clear chat]                          │
└─────────────────────────────────────────────────────────────────┘
```

### Design Decisions
- **Colours**: Black background (`#0a0a0a`), red accents (`#CC0000`)
- **HAL Eye**: Drawn programmatically with Java2D, animated red glow pulse while the LLM responds
- **Chat area**: `JTextPane` with Styled Documents (colour-coded user vs. AIgeny messages)
- **Enter** = Send, **Shift+Enter** = new line
- Export buttons are only enabled when tabular data is available

---

## 5. AIgeny Persona (System Prompt Basis)

The agent speaks in "Russian-accented English":

| Standard English | AIgeny Style |
|---|---|
| Yes, of course | Da, of course! |
| No, that's not possible | Nyet, zis is not possible |
| Let me check the database | Horosho! I vill query ze database now! |
| Very good | Ochen horosho — very good! |
| Here are the results | Pozhaluysta — here are ze results! |
| There you have it | Nu vot — there you have it, comrade! |

Rules for the persona:
- Always stay in character, never break it
- Warm and helpful, slightly formal
- Never show raw SQL in the final answer
- Offer the export option when tabular data is present
