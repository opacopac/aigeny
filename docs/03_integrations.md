# AIgeny - Integration Specification

---

## 1. LLM Integration

### 1.1 Supported Providers

| Provider | Base URL | Default Model | API Key |
|---|---|---|---|
| **Ollama** ⭐ | `http://localhost:11434/v1` | `llama3.1:8b` | `ollama` (no key needed) |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` | Groq API key |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | OpenAI API key |
| Azure OpenAI | `https://RESOURCE.openai.azure.com/openai/deployments/DEPLOY` | `gpt-4o` | Azure API key |
| xAI Grok | `https://api.x.ai/v1` | `grok-3` | xAI API key |
| Anthropic Claude | `https://api.anthropic.com/v1` | `claude-opus-4-5` | Anthropic API key (native adapter) |

### 1.2 API Protocol (OpenAI-compatible)

**Request** (`POST /chat/completions`):
```json
{
  "model": "llama-3.3-70b-versatile",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user",   "content": "Show me all open orders" }
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "query_oracle_db",
        "description": "Execute a read-only SELECT query...",
        "parameters": {
          "type": "object",
          "properties": {
            "sql":         { "type": "string" },
            "description": { "type": "string" }
          },
          "required": ["sql", "description"]
        }
      }
    }
  ],
  "tool_choice": "auto",
  "max_tokens": 8192
}
```

**Response with tool call**:
```json
{
  "choices": [{
    "message": {
      "role": "assistant",
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "query_oracle_db",
          "arguments": "{\"sql\": \"SELECT ...\", \"description\": \"...\"}"
        }
      }]
    }
  }]
}
```

**Tool result** (next request):
```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "name": "query_oracle_db",
  "content": "COLUMN1 | COLUMN2\n-----\nVal1 | Val2"
}
```

### 1.3 Azure Specifics
- Header: `api-key: <key>` instead of `Authorization: Bearer <key>`
- URL suffix: `?api-version=2024-02-15-preview`

### 1.4 Rate Limits (Groq Free Tier, as of 2026)
- ~14,400 requests/day
- ~30 requests/minute
- Sufficient for typical office use with multiple users

---

## 2. Oracle Database Integration

### 2.1 JDBC Connection

| Parameter | Description |
|---|---|
| **JDBC URL** | `jdbc:oracle:thin:@<host>:<port>/<service>` e.g. `jdbc:oracle:thin:@dbserver:1521/PROD` |
| **Driver** | `com.oracle.database.jdbc:ojdbc11:23.4.0.24.05` (Maven Central) |
| **Pool** | HikariCP, max 3 connections, `setReadOnly(true)` |
| **Timeout** | Connection: 15 s |
| **User** | Dedicated read-only DB user (SELECT grants on required schemas only) |

### 2.2 SQL Safety Mechanisms

```
1. Pattern check:  SQL must start with SELECT  (regex: ^\s*SELECT\b.*)
2. Blacklist:      INSERT|UPDATE|DELETE|DROP|TRUNCATE|ALTER|CREATE|MERGE|EXEC|GRANT|REVOKE
3. Confirmation:   User sees the SQL + description and must actively confirm
4. Connection:     conn.setReadOnly(true) at JDBC level
5. DB user:        SELECT grants only at the database level (defence in depth)
```

### 2.3 Schema Loader

The following query runs on app startup:

```sql
-- Tables (up to 300)
SELECT owner, table_name, num_rows
FROM all_tables
WHERE owner NOT IN ('SYS','SYSTEM','OUTLN',...)
ORDER BY owner, table_name
FETCH FIRST 300 ROWS ONLY;

-- Columns per table
SELECT column_name, data_type, data_length, nullable
FROM all_columns
WHERE owner = ? AND table_name = ?
ORDER BY column_id;
```

The result is inserted as formatted text into the system prompt (no character limit - full schema is used).

Fallback if `all_tables` is not accessible → `user_tables` / `user_tab_columns`.

### 2.4 Query Result

`QueryResult` contains:
- `List<String> columns` - column labels
- `List<Map<String, Object>> rows` - up to 5,000 rows
- For the LLM: max 200 rows as text (remainder: "export to see all")
- For export: all rows

---

## 3. Jira Integration

### 3.1 Connection Parameters

| Parameter | Description |
|---|---|
| **Base URL** | e.g. `https://flow.sbb.ch` |
| **API version** | Jira REST API v2 (`/rest/api/2/search`) |
| **Authentication** | HTTP Basic Auth: `Base64(username:token)` |
| **Method** | HTTP GET only (read-only) |

### 3.2 Search Endpoint

```
GET /rest/api/2/search
  ?jql=<URL-encoded JQL>
  &fields=summary,status,assignee,priority,issuetype,created,updated
  &maxResults=20
```

**Example response (simplified)**:
```json
{
  "total": 42,
  "issues": [
    {
      "key": "NOVA-87748",
      "fields": {
        "summary": "Example ticket",
        "status":  { "name": "Open" },
        "assignee":{ "displayName": "Max Mustermann" },
        "priority":{ "name": "Medium" }
      }
    }
  ]
}
```

### 3.3 Tool Parameters (from LLM)

```json
{
  "jql": "project = NOVA AND status = Open ORDER BY created DESC",
  "fields": ["summary", "status", "assignee", "priority"],
  "maxResults": 20
}
```

### 3.4 Own Jira Server vs. Cloud

| Aspect | Own server | Jira Cloud |
|---|---|---|
| URL | `https://flow.sbb.ch` | `https://COMPANY.atlassian.net` |
| Auth | Username + password/token | Email + API token |
| API | REST API v2 | REST API v3 (slightly different JSON structure) |

---

## 4. Export Format

### 4.1 CSV

| Property | Value |
|---|---|
| Encoding | UTF-8 with BOM (for broad client compatibility) |
| Delimiter | Semicolon (`;`) |
| Quoting | RFC 4180 (`"` when value contains semicolon / newline / quote) |
| File extension | `.csv` |


---

## 5. Configuration Format

Stored in `~/.aigeny/aigeny.yml` (override file, never committed to git):

```yaml
aigeny:
  llm:
    provider: ollama
    api-key: ollama
    base-url: http://localhost:11434/v1
    model: llama3.1:8b
  db:
    url: jdbc:oracle:thin:@hostname:1521/SERVICENAME
    username: aigeny_readonly
    password: 'secret'
  jira:
    base-url: https://flow.sbb.ch
    username: firstname.lastname@company.com
    token: your_api_token
```

Alternatively, use environment variables (`AIGENY_DB_PASSWORD=...`) or Docker secret files
(`AIGENY_DB_PASSWORD_FILE=/run/secrets/db_password`). See `docker-compose.yml` for a full example.
