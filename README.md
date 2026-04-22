# AIgeny — README

## AIgeny — AI Data Assistant

AIgeny is a **Spring Boot web application** with a Russian-accented AI assistant for data managers.
Open it in any browser — no installation, no setup wizard, no desktop app required.

It can query Oracle databases (read-only), search Jira tickets, and export results as CSV or Excel.

---

## Quick Start with Docker (recommended)

### Prerequisites
- Docker Desktop installed
- ~5 GB free disk space (for the Ollama LLM model)
- No API key needed — Ollama runs fully locally

### 1. Build & Start

```bash
# Build the JAR first
mvn clean package -DskipTests

# Start the full stack (Ollama + AIgeny)
docker compose up --build
```

> **First run**: Docker automatically downloads the `llama3.1:8b` model (~4.7 GB).
> This takes a few minutes once. Subsequent starts are instant.

### 2. Open in browser

**http://localhost:8080**

---

## Local Start (without Docker)

### Prerequisites
- Java 21+
- Maven 3.8+
- [Ollama](https://ollama.com/download) installed locally (Windows / Mac / Linux)

```bash
# 1. Install Ollama from https://ollama.com/download, then pull the model:
ollama pull llama3.1:8b

# 2. Build and run AIgeny
mvn clean package -DskipTests
java -jar target/aigeny-*.jar
```

Open **http://localhost:8080** in your browser.

---

## Configuration

All settings live in `src/main/resources/application.yml`.
**Never edit this file with secrets for production** — use one of the override mechanisms below.

### Option 1 — External config file (recommended)

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

### Option 2 — Environment variables

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

In `docker-compose.yml` uncomment the relevant `environment:` lines.

---

## LLM Providers

| Provider | Cost | Rate Limits | Privacy | Config |
|---|---|---|---|---|
| **Ollama** ⭐ | Free | None | Fully local | `provider: ollama`, `base-url: http://localhost:11434/v1` |
| Groq | Free tier | ~12k TPM | Cloud | `provider: groq`, `base-url: https://api.groq.com/openai/v1` |
| OpenAI | Paid | — | Cloud | `provider: openai`, `base-url: https://api.openai.com/v1` |
| Azure OpenAI | Paid | — | Cloud | `provider: azure`, `base-url: https://RESOURCE.openai.azure.com/...` |

### Switching providers

Edit `application.yml` or `~/.aigeny/aigeny.yml`:

```yaml
aigeny:
  llm:
    provider: groq
    api-key: "gsk_your_groq_key"
    base-url: "https://api.groq.com/openai/v1"
    model: "llama-3.3-70b-versatile"
```

### GPU Acceleration (Ollama)

Uncomment the `deploy:` section in `docker-compose.yml` for NVIDIA GPU support.
Without GPU: responses take ~10–30 s on CPU. With GPU: ~1–3 s.

---

## REST API

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/chat` | Send a message `{"message":"..."}`, get `{"response":"...","hasExport":true/false}` |
| `POST` | `/api/chat/clear` | Clear session history |
| `POST` | `/api/schema/reload` | Reload DB schema from Oracle |
| `GET` | `/api/status` | LLM/DB/Jira connection status |
| `GET` | `/api/export/csv` | Download last query result as CSV |
| `GET` | `/api/export/excel` | Download last query result as Excel |

---

## Security

- Oracle: only `SELECT` queries allowed (whitelist + read-only JDBC connection)
- Secrets live outside the JAR (external config file or env vars — never committed to git)
- Logs written to `~/.aigeny/aigeny.log`
- Add `.aigeny/aigeny.yml` to `.gitignore`!

---

## Project Structure

```
src/main/java/com/aigeny/
├── AigenyApplication.java       # Spring Boot entry point
├── config/
│   └── AigenyProperties.java    # @ConfigurationProperties (aigeny.*)
├── llm/                         # LLM adapter (Ollama/Groq/OpenAI/Azure)
├── tools/                       # Oracle DB tool, Jira tool
├── db/                          # Schema loader (auto-loads on startup)
├── orchestration/               # Agentic tool-call loop
├── export/                      # CSV & Excel byte[] generation
└── web/                         # REST controllers (chat, export, status)

src/main/resources/
├── application.yml              # Default configuration
└── static/                      # Web frontend
    ├── index.html               # Single-page app with HAL Eye
    ├── css/style.css            # Dark/red theme
    └── js/app.js                # Chat UI, HAL animation, export

Dockerfile                       # Simple Spring Boot container
docker-compose.yml               # AIgeny + Ollama stack
docs/                            # Architecture & spec docs
```
