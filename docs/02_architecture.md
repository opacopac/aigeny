# AIgeny - Architecture

## Overview

AIgeny is a **Spring Boot 3 web application** serving a single-page UI. All configuration is
file- or environment-variable-based - no GUI setup wizard.

---

## System Diagram

```
Browser (any device)
        │  HTTP / REST
        ▼
┌───────────────────────────────────────────────────────┐
│               Spring Boot (port 8080)                  │
│                                                        │
│  Static files          REST Controllers                │
│  /static/              ChatController   → /api/chat   │
│   index.html           ExportController → /api/export │
│   css/style.css        (status, schema reload)        │
│   js/app.js                    │                      │
│                                ▼                      │
│                    OrchestrationService               │
│                    (agentic tool-call loop)            │
│                         │          │                  │
│              ┌──────────┘          └──────────┐       │
│              ▼                                ▼       │
│        LlmClient                          Tools       │
│  OpenAiCompatibleAdapter          OracleDbTool        │
│  (Ollama / Groq / OpenAI /        JiraTool            │
│   Azure - switchable via yml)     SchemaLoader        │
│                                                       │
│  ExportService  → byte[] CSV                          │
└───────────────────────────────────────────────────────┘
        │                         │
        ▼                         ▼
  Ollama :11434             Oracle DB :1521
  (local LLM)               (read-only JDBC)
                             Jira :443 (HTTPS)
```

---

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21, Spring Boot 3.3 |
| Web | Spring MVC, async `CompletableFuture` |
| Frontend | Vanilla HTML/CSS/JS, Canvas API (HAL eye) |
| LLM | OpenAI-compatible REST (Ollama / Groq / OpenAI / Azure / xAI Grok) + Anthropic Claude (native) |
| Oracle | JDBC (`ojdbc11`), HikariCP connection pool |
| Jira | `java.net.http.HttpClient`, Basic Auth, REST API v2 |
| Export | Plain `PrintWriter` (CSV with UTF-8 BOM) |
| Config | Spring Boot `@ConfigurationProperties` |
| Build | Maven 3.8+, `spring-boot-maven-plugin` |
| Container | Docker, Docker Compose |

---

## Project Structure

```
src/main/java/com/tschanz/aigeny/
├── AigenyApplication.java          Spring Boot entry point (@SpringBootApplication)
├── config/
│   └── AigenyProperties.java       @ConfigurationProperties(prefix="aigeny")
├── llm/
│   ├── LlmClient.java              Interface (swap providers easily)
│   ├── OpenAiCompatibleAdapter.java  Works with Ollama/Groq/OpenAI/Azure/xAI Grok
│   ├── AnthropicAdapter.java       Native Claude API adapter
│   └── model/                      Message, ToolCall, ToolDefinition, ChatResponse
├── tools/
│   ├── Tool.java                   Interface
│   ├── OracleDbTool.java           @Service - SQL SELECT via JDBC
│   ├── JiraTool.java               @Service - JQL search via HTTPS
│   ├── QueryResult.java            Column names + row data
│   └── ToolResult.java             Text + optional QueryResult
├── db/
│   └── SchemaLoader.java           @Service - loads schema on startup, caches it
├── orchestration/
│   ├── OrchestrationService.java   @Service - agentic loop, system prompt
│   └── ChatResult.java             Record: response text + last ToolResult
├── export/
│   └── ExportService.java          @Service - byte[] CSV
└── web/
    ├── ChatController.java         POST /api/chat, /api/chat/clear, schema reload, status
    └── ExportController.java       GET /api/export/csv

src/main/resources/
├── application.yml                 Default configuration with comments
├── system-prompt.txt               AIgeny persona + rules (editable without recompile)
└── static/
    ├── index.html                  SPA: HAL eye + chat + export button
    ├── css/style.css               Dark #0a0a0a theme, red #cc0000 accents
    └── js/app.js                   Chat fetch, HAL Canvas animation, Markdown renderer
```

---

## Configuration Model

```
Priority (highest first):
  1. Environment variables  (AIGENY_DB_PASSWORD=...)
  2. ~/.aigeny/aigeny.yml   (external user config)
  3. application.yml        (defaults inside JAR)
```

Spring Boot's relaxed binding maps `aigeny.db.password` → `AIGENY_DB_PASSWORD` automatically.

---

## Docker Architecture

```
docker-compose.yml
├── aigeny          Spring Boot :8080  (depends on ollama)
├── ollama          Ollama API  :11434 (stores models in volume)
└── ollama-init     One-shot: pulls llama3.1:8b on first run

Volumes:
  aigeny-config  →  /root/.aigeny  (external config + logs)
  ollama-models  →  /root/.ollama  (LLM model cache)
```

---

## Session Management

Each browser tab/session holds its own conversation state in the HTTP session:
- `chatHistory` - `List<Message>` (conversation context for the LLM)
- `lastQueryResult` - `QueryResult` (available for CSV download)

Sessions are in-memory (single-instance deployment). For multi-instance, add
Spring Session with Redis.

---

## Security Considerations

| Threat | Mitigation |
|---|---|
| SQL injection / DML | Regex whitelist: only `SELECT` allowed; `DANGEROUS` keyword block |
| DB write access | `setReadOnly(true)` on HikariCP pool |
| Credential exposure | Secrets in external `~/.aigeny/aigeny.yml` or env vars, never in repo |
| LLM data leakage | Use Ollama (local) for sensitive data; no external API calls |
