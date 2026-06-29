# 07 – Package Structure: Screaming Architecture / Clean Architecture Proposal

> Analyse und Verbesserungsvorschlag gemäss Uncle Bob's *Screaming Architecture* und *Clean Architecture* Prinzipien.

---

## 1. Aktuelle Paketstruktur

```
aigeny/
├── AigenyApplication.java
├── Messages.java
├── config/        ← Spring-Infrastruktur + Domain-Interfaces
├── db/            ← nur SchemaLoader
├── export/        ← nur ExportService
├── llm/           ← Port-Interface + alle Adapter-Implementierungen
├── orchestration/ ← Agentic Loop + Confirmation (zur Hälfte)
├── tool/          ← Tool-Abstraktion + Jira/Bitbucket/DB-Implementierungen
└── web/           ← HTTP-Controller + Session-Services + SSE + Confirmation (andere Hälfte)
```

---

## 2. Gefundene Probleme

### Problem 1 – `web/` ist ein God-Package

`web/` vermischt drei konzeptuell vollkommen unterschiedliche Verantwortlichkeiten:

| Kategorie | Klassen |
|-----------|---------|
| HTTP-Delivery | `ChatController`, `ConfirmationController`, `ExportController`, `GitHubController`, `SchemaController`, `TokenController` |
| Application-Services | `SessionHistoryService`, `SessionExportService`, `SessionJiraWriteService`, `TokenService`, `StatusAggregatorService` |
| Confirmation-Infrastruktur | `ConfirmationFutureManager`, `ConfirmationOrchestrator`, `SessionConfirmationService` |
| SSE / Streaming | `SseStreamManager`, `ChatStreamingService`, `CancellationManager` |
| Context-Provider | `JiraContextProvider`, `BitbucketContextProvider` |

Ein HTTP-Framework wie Spring sollte lediglich ein *Delivery-Mechanismus* sein – keine Heimat für Application-Services.

---

### Problem 2 – Die Confirmation-Feature ist zerrissen

Die Bestätigungs-Workflow-Logik ist auf zwei Packages verteilt:

```
orchestration/
  BatchConfirmationContext.java
  BatchConfirmationService.java
  ThreadLocalBatchConfirmationService.java

web/
  ConfirmationFutureManager.java
  ConfirmationOrchestrator.java
  SessionConfirmationService.java
  ConfirmationController.java        ← HTTP-Endpunkt
```

Ein Feature sollte als kohärente Einheit lesbar sein – nicht über zwei Packages verstreut.

---

### Problem 3 – Jira-Concerns sind über 3 Packages verstreut

```
config/
  JiraConfiguration.java            ← Domain-Contract

web/
  JiraContextProvider.java          ← Context-Setup für Jira-Tools
  SessionJiraWriteService.java      ← Session-State für Jira Write-Mode

tool/jira/
  *.java                            ← HTTP-Client, Tools, Operationen, Services
```

Wer die Jira-Integration verstehen will, muss in drei verschiedenen Packages suchen.

---

### Problem 4 – `db/` ist ein Einzelkind-Package

`db/` enthält nur `SchemaLoader`, der konzeptuell zur Oracle-DB-Integration gehört – genau wie `OracleConnectionPool` und `OracleDbTool` in `tool/db/`. Zwei Packages für dieselbe Integration.

---

### Problem 5 – `config/` verschleiert Domain-Contracts

`JiraConfiguration`, `BitbucketConfiguration`, `LlmConfiguration`, `DbConfiguration` sind *Ports* (Interfaces) der jeweiligen Integrationen. In `config/` sind sie technisch einsortiert und gehen unter.

---

### Problem 6 – Top-Level schreit „Spring-Framework", nicht „AI-Agent"

> *"The architecture should scream the intent of the system."* – Robert C. Martin

Aktuelle Top-Level-Packages: `web`, `config`, `db`, `llm`, `orchestration`, `tool`

Man erkennt ein Spring-Web-Projekt mit einer DB und einem LLM-Client. Das eigentliche Ziel – **ein KI-Agent mit Tool-Use-Fähigkeiten** – ist nicht ablesbar.

---

## 3. Verbesserungsvorschlag

### Ziel-Struktur (Feature-First / Screaming Architecture)

```
aigeny/
│
├── AigenyApplication.java
├── Messages.java
│
├── agent/                              ← 🟢 SCHREIT: "Das hier ist ein AI-Agent"
│   ├── AgentService.java               (bisher: OrchestrationService)
│   ├── PromptBuilder.java
│   ├── ToolExecutor.java
│   ├── ChatResult.java
│   ├── WriteToolCallInfo.java
│   ├── CurrentToolCallContext.java
│   └── confirmation/                   ← vollständiges Confirmation-Feature an einem Ort
│       ├── BatchConfirmationContext.java
│       ├── BatchConfirmationService.java
│       ├── ThreadLocalBatchConfirmationService.java
│       ├── ConfirmationFutureManager.java       (aus web/)
│       ├── ConfirmationOrchestrator.java        (aus web/)
│       └── SessionConfirmationService.java      (aus web/)
│
├── tool/                               ← 🟢 SCHREIT: "Der Agent nutzt Tools"
│   ├── Tool.java                       ← Port (Interface)
│   ├── AbstractTool.java
│   ├── ToolResult.java
│   ├── QueryResult.java
│   │
│   ├── jira/                           ← vollständige Jira-Integration an einem Ort
│   │   ├── (alle bisherigen tool/jira/** Dateien)
│   │   ├── JiraContextProvider.java        (aus web/)
│   │   └── JiraSessionWriteService.java    (aus web/, umbenannt)
│   │
│   ├── bitbucket/                      ← vollständige Bitbucket-Integration
│   │   ├── (alle bisherigen tool/bitbucket/** Dateien)
│   │   └── BitbucketContextProvider.java   (aus web/)
│   │
│   └── database/                       ← vollständige Oracle-DB-Integration
│       ├── OracleConnectionPool.java
│       ├── OracleDbTool.java
│       └── OracleSchemaLoader.java         (aus db/, umbenannt)
│
├── llm/                                ← LLM-Kommunikation (Port + Adapter)
│   ├── LlmClient.java                  ← Port-Interface
│   ├── LlmAdapterFactory.java
│   ├── model/
│   ├── anthropic/
│   ├── github/
│   └── openai/
│
├── session/                            ← 🟢 User-Session-State (aus web/ herausgelöst)
│   ├── ChatHistoryService.java         (= SessionHistoryService)
│   ├── ExportSessionService.java       (= SessionExportService)
│   ├── CancellationSessionService.java (= SessionCancellationService)
│   └── TokenService.java               (aus web/)
│
├── api/                                ← HTTP-Delivery (umbenannt von web/)
│   ├── ChatController.java
│   ├── ConfirmationController.java
│   ├── ExportController.java
│   ├── GitHubController.java
│   ├── SchemaController.java
│   ├── TokenController.java
│   ├── ChatStreamingService.java
│   ├── SseStreamManager.java
│   ├── CancellationManager.java
│   ├── ExecutionContextManager.java
│   └── StatusAggregatorService.java
│
├── export/                             ← CSV-Export-Feature
│   └── ExportService.java
│
└── infrastructure/                     ← Spring-Verdrahtung (umbenannt von config/)
    ├── AigenyProperties.java
    ├── ConfigBeans.java
    ├── ConfigurationValidator.java
    ├── SecretFileResolver.java
    └── LlmConfig.java
```

---

## 4. Migrationsmassnahmen (priorisiert)

### 🔴 Hohe Priorität

#### M1 – `web/` aufteilen in `api/`, `session/`, `agent/confirmation/`

Das ist das dringendste Problem mit dem grössten Gewinn.

| Klasse (aus `web/`) | Ziel |
|---------------------|------|
| `ChatController` | `api/` |
| `ConfirmationController` | `api/` |
| `ExportController` | `api/` |
| `GitHubController` | `api/` |
| `SchemaController` | `api/` |
| `TokenController` | `api/` |
| `ChatStreamingService` | `api/` |
| `SseStreamManager` | `api/` |
| `CancellationManager` | `api/` |
| `ExecutionContextManager` | `api/` |
| `StatusAggregatorService` | `api/` |
| `SessionHistoryService` | `session/ChatHistoryService` |
| `SessionExportService` | `session/ExportSessionService` |
| `SessionCancellationService` | `session/CancellationSessionService` |
| `TokenService` | `session/` |
| `ConfirmationFutureManager` | `agent/confirmation/` |
| `ConfirmationOrchestrator` | `agent/confirmation/` |
| `SessionConfirmationService` | `agent/confirmation/` |
| `JiraContextProvider` | `tool/jira/` |
| `BitbucketContextProvider` | `tool/bitbucket/` |
| `SessionJiraWriteService` | `tool/jira/JiraSessionWriteService` |
| `HistoryManager` | `session/` |

#### M2 – `db/` in `tool/database/` integrieren

```
db/SchemaLoader.java  →  tool/database/OracleSchemaLoader.java
tool/db/*             →  tool/database/*
```

Das Package `db/` wird damit aufgelöst.

---

### 🟡 Mittlere Priorität

#### M3 – `config/` → `infrastructure/`

`infrastructure` beschreibt klarer, dass hier Spring-Beans, Property-Bindings und Infrastruktur-Verdrahtung stattfindet.

#### M4 – `OrchestrationService` → `AgentService`

`Orchestration` ist technischer Jargon. `Agent` spiegelt die Fachlichkeit des Systems wider und macht die Klasse sofort verständlich.

---

### 🟢 Niedrige Priorität / Optional

#### M5 – Configuration-Interfaces näher an die Integrationen

`JiraConfiguration`, `BitbucketConfiguration`, `LlmConfiguration`, `DbConfiguration` sind Ports/Contracts. Sie könnten in den jeweiligen Tool-Packages (`tool/jira/`, `tool/bitbucket/`, `llm/`, `tool/database/`) leben und damit die Packages zu eigenständigen, abgeschlossenen Integration-Modulen machen.

---

## 5. Erwarteter Nutzen

| Aspekt | Vorher | Nachher |
|--------|--------|---------|
| Lesbarkeit (neu) | Sieht Spring-App | Sieht AI-Agent mit Tools |
| Jira-Code finden | 3 Packages durchsuchen | alles in `tool/jira/` |
| Confirmation-Feature | 2 Packages | 1 Package (`agent/confirmation/`) |
| `web/` Grösse | 22 Klassen | ~11 Klassen (nur HTTP) |
| Session-Management | versteckt in `web/` | eigenes `session/`-Package |
| Framework-Kopplung | `web/` enthält App-Logik | `api/` enthält nur Delivery |

