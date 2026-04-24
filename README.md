# AIgeny - README

## AIgeny - AI Data Assistant

AIgeny is a **Spring Boot web application** with a Russian-accented AI assistant for NOVA data managers.
Open it in any browser - no installation, no setup wizard, no desktop app required.

It can query Oracle databases (read-only), search and update Jira tickets, and export results as CSV.

---

## Quick Start with Docker (recommended)

### Prerequisites
- Docker Desktop installed
- An API key for your chosen LLM provider (e.g. Anthropic Claude) **or** ~5 GB free disk space if using Ollama locally

### 1. Configure your LLM provider

Create `~/.aigeny/aigeny.yml` with your provider settings (see [Configuration](#configuration) below).
Example for Claude Sonnet (default/recommended):

```yaml
aigeny:
  llm:
    provider: claude
    api-key: "your_anthropic_api_key"
    model: "claude-sonnet-4-5"
```

### 2. Build & Start

```bash
# Build the JAR first
mvn clean package -DskipTests

# Start AIgeny (+ Ollama if configured)
docker compose up --build
```

> **Using Ollama instead?** The `ollama-init` container automatically pulls the `llama3.1:8b` model (~4.7 GB) on first run.
> This takes a few minutes once. Subsequent starts are instant.

### 3. Open in browser

**http://localhost:8080**

---

## Local Start (without Docker)

### Prerequisites
- Java 21+
- Maven 3.8+
- An API key for your chosen LLM provider **or** [Ollama](https://ollama.com/download) for fully local inference

```bash
# Build and run AIgeny
mvn clean package -DskipTests
java -jar target/aigeny-*.jar
```

> **Using Ollama?** Install it from https://ollama.com/download.
> On Windows/Mac it starts as a background service automatically.
> On Linux run `ollama serve`, then pull the model once: `ollama pull llama3.1:8b`

Open **http://localhost:8080** in your browser.

---

## Configuration

All settings live in `src/main/resources/application.yml`.
**Never edit this file with secrets for production** - use one of the override mechanisms below.

### Option 1 - External config file (recommended)

Create `~/.aigeny/aigeny.yml` (or `/root/.aigeny/aigeny.yml` in Docker) with only the values you want to override:

```yaml
aigeny:
  llm:
    provider: ollama
    base-url: http://localhost:11434/v1
    model: llama3.1:8b

  db:
    url: "jdbc:oracle:thin:@myhost:1521/MYSERVICE"
    username: "readonly_user"
    password: "secret"

  jira:
    base-url: "https://flow.sbb.ch"
    username: "john.doe"
    token: "your_api_token"
```

### Option 2 - Environment variables

Spring Boot maps `aigeny.db.password` → `AIGENY_DB_PASSWORD` automatically:

```bash
export AIGENY_DB_URL=jdbc:oracle:thin:@myhost:1521/MYSERVICE
export AIGENY_DB_USERNAME=readonly_user
export AIGENY_DB_PASSWORD=secret
export AIGENY_JIRA_BASE_URL=https://flow.sbb.ch
export AIGENY_JIRA_USERNAME=john.doe
export AIGENY_JIRA_TOKEN=your_api_token
java -jar target/aigeny-*.jar
```

For **Docker secrets** (files instead of plain-text env vars), use the `_FILE` variant:

```bash
# Point to a file containing the secret value (e.g. /run/secrets/db_password)
AIGENY_DB_PASSWORD_FILE=/run/secrets/db_password
AIGENY_JIRA_TOKEN_FILE=/run/secrets/jira_token
AIGENY_LLM_API_KEY_FILE=/run/secrets/llm_api_key
```

AIgeny reads the file content at startup and uses it as the value. See `docker-compose.yml` for a ready-to-use example.

In `docker-compose.yml` uncomment the relevant `environment:` lines.

---

## LLM Providers

| Provider | Cost | Rate Limits | Privacy | Config |
|---|---|---|---|---|
| **Anthropic Claude** ⭐ | Paid | - | Cloud | `provider: claude`, `base-url: https://api.anthropic.com/v1` |
| Ollama | Free | None | Fully local | `provider: ollama`, `base-url: http://localhost:11434/v1` |
| Groq | Free tier | ~12k TPM | Cloud | `provider: groq`, `base-url: https://api.groq.com/openai/v1` |
| OpenAI | Paid | - | Cloud | `provider: openai`, `base-url: https://api.openai.com/v1` |
| Azure OpenAI | Paid | - | Cloud | `provider: azure`, `base-url: https://RESOURCE.openai.azure.com/...` |
| xAI Grok | Paid | - | Cloud | `provider: grok`, `base-url: https://api.x.ai/v1` |

### Switching providers

Edit `application.yml` or `~/.aigeny/aigeny.yml`:

```yaml
aigeny:
  llm:
    provider: claude
    api-key: "your_anthropic_api_key"
    base-url: "https://api.anthropic.com/v1"
    model: "claude-sonnet-4-5"
```

Or for Groq:

```yaml
aigeny:
  llm:
    provider: groq
    api-key: "gsk_your_groq_key"
    base-url: "https://api.groq.com/openai/v1"
    model: "llama-3.3-70b-versatile"
```


---

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | Send a message `{"message":"..."}`, get `{"response":"...","hasExport":true/false}` |
| `POST` | `/api/chat/clear` | Clear session history |
| `POST` | `/api/schema/reload` | Reload DB schema from Oracle |
| `GET` | `/api/status` | LLM/DB/Jira connection status |
| `GET` | `/api/export/csv` | Download last query result as CSV |

---

## Security

- Oracle: only `SELECT` queries allowed (read-only DB user)
- Secrets live outside the JAR (external config / secret files)
- Logs written to `~/.aigeny/aigeny.log`
- Add `.aigeny/aigeny.yml` to `.gitignore`!

---

## Project Structure

```
src/main/java/com/tschanz/aigeny/
├── AigenyApplication.java       # Spring Boot entry point
├── Messages.java                # Message constants
├── config/
│   ├── AigenyProperties.java    # @ConfigurationProperties (aigeny.*)
│   └── LlmConfig.java           # LLM bean configuration
├── llm/                         # LLM adapters (Ollama/Groq/OpenAI/Azure/Grok/Claude)
│   ├── LlmClient.java
│   ├── AnthropicAdapter.java
│   ├── OpenAiCompatibleAdapter.java
│   └── model/
├── llm_tool/                    # Tool definitions for LLM function calling
│   ├── Tool.java
│   ├── ToolResult.java
│   ├── QueryResult.java
│   ├── db/
│   │   └── OracleDbTool.java    # Oracle DB query tool
│   └── jira/
│       ├── JiraTool.java        # Jira search tool
│       ├── AddJiraCommentTool.java
│       ├── UpdateJiraIssueTool.java
│       ├── JiraWriteExecutor.java
│       ├── JiraTokenContext.java
│       ├── PendingJiraAction.java
│       └── PendingJiraActionContext.java
├── db/                          # Schema loader (auto-loads on startup)
│   └── SchemaLoader.java
├── export/                      # CSV export (byte[] generation)
│   └── ExportService.java
├── orchestration/               # Agentic tool-call loop
│   ├── OrchestrationService.java
│   └── ChatResult.java
└── web/                         # REST controllers (chat, export, status)
    ├── ChatController.java
    └── ExportController.java

src/main/resources/
├── application.yml              # Default configuration
├── system-prompt.txt            # LLM system prompt
├── messages.properties          # UI message strings
├── logback.xml                  # Logging configuration
└── static/                      # Web frontend
    ├── index.html               # Single-page app with HAL Eye
    ├── css/style.css            # Dark/red theme
    └── js/app.js                # Chat UI, HAL animation, export

Dockerfile                       # Simple Spring Boot container
docker-compose.yml               # AIgeny + Ollama stack
docs/                            # Architecture & spec docs
```
