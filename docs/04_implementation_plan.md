# AIgeny — Implementation Plan

## Status

| Phase | Task | Status |
|---|---|---|
| 1 | Maven project with Spring Boot parent | ✅ Done |
| 2 | `AigenyProperties` (`@ConfigurationProperties`) | ✅ Done |
| 3 | `application.yml` with full documentation | ✅ Done |
| 4 | LLM models (Message, ToolCall, ToolDefinition, ChatResponse) | ✅ Done |
| 5 | `OpenAiCompatibleAdapter` (Ollama/Groq/OpenAI/Azure) | ✅ Done |
| 6 | `SchemaLoader` (`@Service`, startup loading, cache, reload) | ✅ Done |
| 7 | `OracleDbTool` (`@Service`, SELECT whitelist, HikariCP) | ✅ Done |
| 8 | `JiraTool` (`@Service`, JQL, Basic Auth) | ✅ Done |
| 9 | `OrchestrationService` (agentic loop, system prompt, `ChatResult`) | ✅ Done |
| 10 | `ExportService` (CSV bytes) | ✅ Done |
| 11 | `ChatController` (REST, async, `HttpSession`) | ✅ Done |
| 12 | `ExportController` (CSV download) | ✅ Done |
| 13 | Web frontend (HAL Canvas, dark theme, chat, export buttons) | ✅ Done |
| 14 | `Dockerfile` (simple Spring Boot image) | ✅ Done |
| 15 | `docker-compose.yml` (AIgeny + Ollama + init) | ✅ Done |
| 16 | Documentation (README, docs/*.md) | ✅ Done |

---

## Architecture Change Log

### v2.0 — Swing → Spring Boot Web App

| Before (v1) | After (v2) |
|---|---|
| Standalone Swing desktop app | Spring Boot web app (port 8080) |
| noVNC + Xvfb for browser access | Native browser access, no VNC needed |
| `AppConfig` + AES-encrypted properties | `@ConfigurationProperties` + external YAML |
| GUI setup wizard (`SetupWizard.java`) | Edit `~/.aigeny/aigeny.yml` or use env vars |
| Swing `JOptionPane` SQL confirmation | SQL logged, shown in chat; SELECT-only enforced |
| `JFileChooser` export dialogs | HTTP file download (`/api/export/csv`) |
| HAL eye in `HalEyePanel.java` (Swing) | HAL eye in `app.js` (Canvas API) |
| FlatLaf dark theme | CSS dark theme (`#0a0a0a` / `#cc0000`) |

---

## Test Checklist

### Before going live:

- [ ] `mvn clean package -DskipTests` → BUILD SUCCESS
- [ ] `java -jar target/aigeny-*.jar` → `http://localhost:8080` opens
- [ ] Welcome message appears with HAL eye animation
- [ ] LLM responds to "hello" in Russian accent
- [ ] DB not configured → graceful message, no crash
- [ ] DB configured → schema loads on startup, table count shown in status box
- [ ] Question about data → SQL generated, DB queried, results shown
- [ ] Export CSV → file downloads with correct data
- [ ] Jira search → issues returned in table format
- [ ] Clear chat → history wiped, welcome message re-appears
- [ ] Reload schema → table count updates
- [ ] Docker compose up → accessible at http://localhost:8080
- [ ] Env var override works: `AIGENY_LLM_MODEL=llama3.2:3b java -jar ...`

---

## Known Limitations (v2)

| Limitation | Workaround / Future Fix |
|---|---|
| No SQL confirmation dialog | SQL is logged to `aigeny.log`; SELECT-only enforced at code level |
| Session state is in-memory | Add Spring Session + Redis for multi-instance deployment |
| No streaming (LLM response arrives at once) | Add SSE streaming in v3 |
| Single-user assumption per session | Fine for current use case |
| Schema limited to 6,000 chars in prompt | Removed — full schema is now always injected |
