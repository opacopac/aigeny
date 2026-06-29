# SOLID-Analyse & Refactoring-Proposal

**Datum:** 2026-06-29
**Status:** Analyse abgeschlossen – aktualisierte Version
**Vorgängerdokument:** v1.0 vom 2026-06-26 (veraltet, da zwischenzeitliche Refactorings bereits umgesetzt)

---

## Executive Summary

Das AIgeny-Projekt weist eine solide Grundstruktur auf: Das Strategy-Pattern für LLM-Adapter, die
`Tool`-Plugin-Architektur und die Aufteilung der Session-Verwaltung in dedizierte Sub-Manager
(`ConfirmationFutureManager`, `CancellationManager`, `HistoryManager`) sind vorbildlich.

Dennoch bestehen in mehreren Bereichen klare SOLID-Verstösse, vor allem durch direkte Abhängigkeiten
auf statische `ThreadLocal`-Klassen, duplizierte HTTP-Client-Erzeugung und einen zu breiten
`ChatController`. Der Plan priorisiert die Massnahmen nach Aufwand/Nutzen-Verhältnis.

### Status gegenüber v1.0

| Befund v1.0 | Status heute |
|---|---|
| `ChatStreamingService` – 404 Zeilen, 6 Verantwortlichkeiten | ✅ **Bereits behoben** – 140 Zeilen, `ExecutionContextManager`, `ConfirmationOrchestrator` etc. extrahiert |
| `ChatSessionService` – monolithisch | ✅ **Bereits behoben** – delegiert an `ConfirmationFutureManager`, `CancellationManager`, `HistoryManager` |
| LLM-Adapter ohne Factory-Pattern | ✅ **Bereits behoben** – `LlmAdapterFactory`-Registry vorhanden |
| `ChatController` line 307: `new ChatSessionService()` | ✅ **Bereits behoben** – kein Direktaufruf mehr |
| Neue Befunde (siehe unten) | 🔴 Offen |

---

## Befunde nach SOLID-Prinzip

### S – Single Responsibility Principle

#### 🔴 S-1: `ChatController` – zu viele Endpunkte (303 Zeilen, 7 Verantwortlichkeiten)

**Datei:** `src/main/java/com/tschanz/aigeny/web/ChatController.java`

Der Controller vereint aktuell folgende Zuständigkeiten:

| Endpunkt | Verantwortlichkeit |
|---|---|
| `POST /api/chat` | Non-streaming Chat |
| `POST /api/chat/stream` | SSE-Streaming Chat |
| `POST /api/jira/confirm-decision` | Einzelne Jira-Bestätigung |
| `POST /api/jira/batch-confirm-decision` | Batch-Jira-Bestätigung |
| `POST /api/chat/cancel` | Abbruch |
| `POST /api/chat/clear` | History leeren |
| `POST /api/schema/reload` | Schema-Reload |
| `GET /api/status` | Status-Abfrage |
| `POST/DELETE /api/jira/token` + Write-Mode | Jira-Token/-Modus |
| `POST /api/bitbucket/token` | Bitbucket-Token |

**Zusatzproblem:** Der Non-Streaming-Pfad (`POST /api/chat`, Zeilen 100–104) setzt
`JiraTokenContext`, `JiraWriteContext` und `BitbucketTokenContext` direkt und manuell –
während der Streaming-Pfad korrekt den `ExecutionContextManager` nutzt. Das ist inkonsistent
und ein verstecktes Duplikat.

**Vorschlag:** Drei Controller statt einem:

```
ChatController          → POST /api/chat, POST /api/chat/stream, POST /api/chat/cancel,
                          POST /api/chat/clear, GET /api/status
ConfirmationController  → POST /api/jira/confirm-decision,
                          POST /api/jira/batch-confirm-decision
TokenController         → POST/DELETE /api/jira/token, POST /api/jira/write-mode,
                          POST /api/bitbucket/token
SchemaController        → POST /api/schema/reload  (oder in SystemController)
```

Der Non-Streaming-Pfad in `ChatController.chat()` soll zudem
`ExecutionContextManager.setupContexts()` / `cleanupAllContexts()` nutzen statt
die ThreadLocal-Klassen direkt anzusprechen.

---

#### 🟡 S-2: `QueryJiraTool` – HTTP, Auth, zwei Abfragepfade und Response-Parsing (298 Zeilen)

**Datei:** `src/main/java/com/tschanz/aigeny/llm_tool/jira/QueryJiraTool.java`

Die Klasse besitzt gleichzeitig:
- Token-Auflösung (Zeilen 95–102)
- Eigene `HttpClient`-Instanz (Zeilen 48–51) – obwohl `JiraHttpClient` als Spring-Bean existiert
- JQL-Suche (`searchByJql`) und Issue-Direktabruf (`fetchIssueByKey`) als zwei vollständige Abfragepfade
- Komplexes Response-Parsing inkl. Markdown-Formatierung für Single-Issue (80+ Zeilen, Zeilen 182–263)
  und Listenformat (Zeilen 265–296)

**Vorschlag:**
- `QueryJiraTool` nutzt den injizierten `JiraHttpClient` (GET-Methode ergänzen)
- Parsing-Logik in eine `JiraIssueFormatter`-Klasse extrahieren

---

#### 🟡 S-3: `CloneJiraIssueTool` – eigener HTTP-Client statt `JiraHttpClient` (245 Zeilen)

**Datei:** `src/main/java/com/tschanz/aigeny/llm_tool/jira/CloneJiraIssueTool.java`

`CloneJiraIssueTool` erzeugt seinen eigenen `java.net.http.HttpClient` in Zeile 55–57
(mit identischen Timeout-Werten wie `QueryJiraTool`), obwohl `JiraHttpClient` exakt für
diesen Zweck existiert und bereits als Spring-Component registriert ist. Die GET-Hilfsmethode
`getJson()` (Zeilen 221–243) dupliziert Logik, die in `JiraHttpClient` gehört.

**Vorschlag:**
- `JiraHttpClient` um eine `get(url, auth)`-Methode erweitern
- `CloneJiraIssueTool` injiziert `JiraHttpClient` statt eigenen Client zu erzeugen

---

#### 🟡 S-4: `OracleDbTool` – Verbindungspool-Lifecycle gemischt mit Query-Logik (164 Zeilen)

**Datei:** `src/main/java/com/tschanz/aigeny/llm_tool/db/OracleDbTool.java`

`OracleDbTool` verwaltet den `HikariDataSource`-Pool selbst (Lazy-Init + Caching, Zeilen 55–80)
und führt gleichzeitig die eigentliche SQL-Abfrage durch. Das bedeutet:
- Der Pool wird niemals explizit geschlossen (kein `@PreDestroy`)
- Die Konfigurationsvalidierung (`configValidator.isDbConfigured`) wird zweifach aufgerufen
  (Zeilen 55 und 104)
- Unit-Tests des Query-Pfades erfordern immer einen echten Connection-Pool

**Vorschlag:**
- Neue Spring-Komponente `OracleConnectionPool` (oder `@Bean` in `ConfigBeans`) mit
  `@PostConstruct`/`@PreDestroy`
- `OracleDbTool` erhält den DataSource via Konstruktor-Injection (kann `null` sein, wenn DB
  nicht konfiguriert)

---

#### 🟢 S-5: Frontend `app.js` – `reloadSchema()` mit gemischten Concerns

**Datei:** `src/main/resources/static/js/app.js` (Zeilen 123–143)

`reloadSchema()` kombiniert Fetch-Logik, Button-State-Management und direkte
`appendMessage`-Aufrufe. Das ist akzeptabel in der aktuellen Grösse, könnte aber in ein
eigenes Modul `schema-panel.js` extrahiert werden, wenn weitere Panel-Aktionen hinzukommen.

---

### O – Open/Closed Principle

#### 🟡 O-1: ThreadLocal-Kontext-System nicht erweiterbar

Eine neue Integration (z. B. ein drittes Produktivsystem mit eigenem Token) erfordert
Änderungen an vier Stellen:
1. Neue `XyzTokenContext`-Klasse erstellen
2. `ExecutionContextManager.setupContexts()` erweitern
3. `ExecutionContextManager.cleanupAllContexts()` erweitern
4. `ChatController.chat()` (Non-Streaming-Pfad) manuell anpassen

**Vorschlag:** Ein `ContextProvider`-Interface:

```java
public interface ContextProvider {
    void setup(String token, boolean writeEnabled);
    void cleanup();
}
```

`ExecutionContextManager` iteriert über alle registrierten `ContextProvider`-Beans.
Neue Integrationen erfordern nur eine neue Bean – keine Änderung am Manager.

---

#### 🟡 O-2: Frontend `chat-stream.js` – Bestätigungs-Typen hardcodiert

Neue SSE-Event-Typen für Bestätigungen (z. B. eine dritte Bestätigungsart) erfordern
eine Modifikation von `chat-stream.js` und `chat-renderer.js`. Das Handler-Map-Pattern
wäre eine elegantere Alternative:

```javascript
// In chat-stream.js – erweiterbar ohne Modifikation
const SSE_HANDLERS = {
  tool_call:                    (data) => renderer.updateTypingIndicator(data.toolName, data.description),
  intermediate:                 (data) => { renderer.finalizeTypingIndicator(); renderer.appendMessage('aigeny', data.response); renderer.showTypingIndicator(); },
  confirmation_required:        (data) => { renderer.finalizeTypingIndicator(); renderer.showJiraConfirmation(data.description); },
  batch_confirmation_required:  (data) => { renderer.finalizeTypingIndicator(); renderer.showJiraBatchConfirmation(data.actions); },
  done:                         (data) => { renderer.finalizeTypingIndicator(); if (data.response) renderer.appendMessage('aigeny', data.response); if (data.hasExport) setExportEnabledFn(true); },
  cancelled:                    ()     => { renderer.removeTypingIndicator(); renderer.appendMessage('aigeny', '_Abgebrochen, Towarischtsch._'); },
  error:                        (data) => { renderer.removeTypingIndicator(); renderer.appendMessage('aigeny', 'Njet! ' + (data.message || '?')); },
};
// processStream() ruft dann: SSE_HANDLERS[data.type]?.(data);
```

---

### L – Liskov Substitution Principle

**✅ Eingehalten.** Die `Tool`-Hierarchie (`AbstractTool` → alle konkreten Tools) und die
`LlmClient`-Implementierungen respektieren die Schnittstellenverträge vollständig. Keine LSP-Verstösse gefunden.

> **Hinweis:** Das Vorgängerdokument (v1.0) sah hier einen Verstoss durch `requiresConfirmation()`.
> Das `default boolean requiresConfirmation() { return false; }` in `Tool` ist jedoch ein gültiges
> Default-Verhalten für Read-Tools – kein echtes LSP-Problem.

---

### I – Interface Segregation Principle

#### 🟡 I-1: `ChatSessionService` als breite Fassade

**Datei:** `src/main/java/com/tschanz/aigeny/web/ChatSessionService.java`

Obwohl `ChatSessionService` bereits intern an `ConfirmationFutureManager`, `CancellationManager`
und `HistoryManager` delegiert, bleibt die öffentliche Schnittstelle mit 20+ Methoden sehr breit.
Konsumenten wie `ChatStreamingService` benötigen davon nur einen Bruchteil, müssen aber die gesamte
Klasse importieren.

**Vorschlag:** Schmale Interfaces einführen:

```java
public interface SessionHistoryService {
    List<Message> getOrCreateHistory(HttpSession session);
    void clearHistory(HttpSession session);
}

public interface SessionExportService {
    void setLastQueryResult(HttpSession session, QueryResult result);
    QueryResult getLastQueryResult(HttpSession session);
    boolean hasQueryResult(HttpSession session);
}

public interface SessionCancellationService {
    AtomicBoolean createCancelFlag(HttpSession session);
    boolean triggerCancellation(HttpSession session);
    void clearCancelFlag(HttpSession session);
}
```

`ChatSessionService` implementiert alle drei Interfaces und bleibt als zentraler Bean erhalten.
Konsumenten injizieren nur das benötigte Interface.

---

### D – Dependency Inversion Principle

#### 🔴 D-1: Write-Tools abhängig von statischem `ConfirmationContext`

**Dateien:**
- `src/main/java/com/tschanz/aigeny/llm_tool/jira/UpdateJiraIssueTool.java` (Zeilen 123–130)
- `src/main/java/com/tschanz/aigeny/llm_tool/jira/CreateJiraIssueTool.java` (Zeilen 97–119)
- `src/main/java/com/tschanz/aigeny/llm_tool/jira/CloneJiraIssueTool.java` (Zeilen 209–217)

Alle drei Write-Tools rufen direkt `ConfirmationContext.isAvailable()` und
`ConfirmationContext.get().requestConfirmation(...)` auf – eine versteckte Abhängigkeit auf eine
konkrete statische Klasse, die weder injizierbar noch mockbar ist.

**Symptom:** Wenn `ConfirmationContext` nicht gesetzt ist, geben die Tools
`"jira.error.no_streaming_context"` zurück statt dass dies durch das DI-Framework erzwungen wird.

**Vorschlag:** Das `ConfirmationContext.Handler`-Interface aus `ConfirmationContext` herauslösen
und als injizierbaren Spring-Scope-Bean anbieten:

```java
// Bereits als @FunctionalInterface vorhanden – als Bean registrieren:
@Bean
@SessionScope  // oder RequestScope – je nach Threading-Modell
public ConfirmationContext.Handler confirmationHandler(ConfirmationOrchestrator orchestrator, ...) {
    return (desc, action) -> orchestrator.handleSingleConfirmation(...);
}

// Write-Tools erhalten den Handler via Konstruktor:
public UpdateJiraIssueTool(JiraConfiguration config, ObjectMapper mapper,
                           ConfirmationContext.Handler confirmationHandler) { ... }
```

Alternativ (minimaler Aufwand): Den Null-Check durch eine `Optional`-Injection ersetzen und
die statische ThreadLocal-Schnittstelle beibehalten, aber hinter einem `ConfirmationService`-Interface
verstecken.

---

#### 🔴 D-2: `OrchestrationService` greift direkt auf `BatchConfirmationContext` und `ConfirmationContext` zu

**Datei:** `src/main/java/com/tschanz/aigeny/orchestration/OrchestrationService.java`
(Zeilen 155–175, Methode `preScanAndBatchConfirm`)

`OrchestrationService` greift direkt auf `BatchConfirmationContext.isAvailable()`,
`BatchConfirmationContext.get().apply(...)` und `ConfirmationContext.setPreApprovedDecisions()`
zu. Das sind drei konkrete statische Klassen als versteckte Abhängigkeiten, die weder injiziert
noch in Tests gemockt werden können.

**Vorschlag:** Ein `BatchConfirmationService`-Interface:

```java
public interface BatchConfirmationService {
    boolean isAvailable();
    Map<String, Boolean> requestBatchConfirmation(List<WriteToolCallInfo> writeToolInfos);
    void applyPreApprovedDecisions(Map<String, Boolean> decisions);
}
```

`OrchestrationService` bekommt diesen Service per Konstruktor-Injection.
Die ThreadLocal-basierten Kontexte sind nur eine mögliche Implementierung.

---

#### 🟡 D-3: `ChatController` Non-Streaming-Pfad setzt ThreadLocals direkt

**Datei:** `src/main/java/com/tschanz/aigeny/web/ChatController.java` (Zeilen 100–104 und 124–127)

```java
// Non-Streaming-Pfad (POST /api/chat) – direkte statische Abhängigkeiten:
JiraTokenContext.set(jiraToken);
JiraWriteContext.set(jiraWriteEnabled);
BitbucketTokenContext.set(bitbucketToken);
PendingJiraActionContext.clear();
// ... (und im finally-Block wieder .clear())
```

Der Streaming-Pfad delegiert korrekt an `ExecutionContextManager`. Die Inkonsistenz erzeugt
doppelten Wartungsaufwand und macht den Non-Streaming-Pfad schwerer testbar.

**Vorschlag:** `ExecutionContextManager.setupContexts()` / `cleanupAllContexts()` auch im
Non-Streaming-Pfad verwenden (bereits vorhanden, muss nur aufgerufen werden).

---

#### 🟡 D-4: `QueryJiraTool` und `CloneJiraIssueTool` erstellen eigene `HttpClient`-Instanzen

Beide Tools erzeugen `HttpClient.newBuilder()...build()` im Konstruktor, obwohl
`JiraHttpClient` bereits als injizierbare Spring-`@Component` existiert. Das bedeutet:
- Duplizierter Code mit identischen Timeout-Konfigurationen
- Nicht mockbar in Tests
- `JiraHttpClient` wird von diesen Tools gar nicht genutzt

**Vorschlag:** `JiraHttpClient` um eine `get(url, authHeader)`-Methode ergänzen;
beide Tools injizieren `JiraHttpClient` statt eigene Instanzen zu erzeugen.

---

## Zusammenfassung der Befunde

| ID | Prinzip | Klasse / Datei | Schwere | Aufwand |
|----|---------|----------------|---------|---------|
| S-1 | SRP | `ChatController` | 🔴 Hoch | Mittel |
| S-2 | SRP | `QueryJiraTool` | 🟡 Mittel | Gering |
| S-3 | SRP | `CloneJiraIssueTool` | 🟡 Mittel | Gering |
| S-4 | SRP | `OracleDbTool` | 🟡 Mittel | Mittel |
| S-5 | SRP | `app.js` | 🟢 Niedrig | Gering |
| O-1 | OCP | `ExecutionContextManager` | 🟡 Mittel | Mittel |
| O-2 | OCP | `chat-stream.js` | 🟢 Niedrig | Gering |
| I-1 | ISP | `ChatSessionService` | 🟡 Mittel | Gering |
| D-1 | DIP | Write-Tools (`Update/Create/Clone`) | 🔴 Hoch | Hoch |
| D-2 | DIP | `OrchestrationService` | 🔴 Hoch | Mittel |
| D-3 | DIP | `ChatController` (non-streaming) | 🟡 Mittel | Gering |
| D-4 | DIP | `QueryJiraTool`, `CloneJiraIssueTool` | 🟡 Mittel | Gering |

---

## Empfohlene Reihenfolge

### Phase 1 – Quick Wins (geringe Risiken, sofortiger Nutzen)

1. **D-3 – `ChatController` Non-Streaming**: `ExecutionContextManager` auch im `POST /api/chat`-Pfad
   nutzen – 1 Stunde Arbeit, eliminiert Code-Duplikat.
2. **D-4 – `JiraHttpClient` erweitern + nutzen**: GET-Methode in `JiraHttpClient` ergänzen;
   `QueryJiraTool` und `CloneJiraIssueTool` umstellen – 0.5 Tage.
3. **O-2 – Handler-Map in `chat-stream.js`**: Magic-Strings durch Handler-Map ersetzen – 1 Stunde.

### Phase 2 – Strukturelle Verbesserungen

4. **S-1 – `ChatController` aufteilen**: `ConfirmationController`, `TokenController` extrahieren –
   1–2 Tage (inkl. Frontend-Anpassungen für neue Endpunkt-Pfade prüfen).
5. **S-4 – `OracleConnectionPool` extrahieren**: Pool-Lifecycle aus `OracleDbTool` herauslösen –
   0.5–1 Tag.
6. **I-1 – Schmale Interfaces für `ChatSessionService`** – 0.5 Tage.

### Phase 3 – Architekturelle Verbesserungen (höheres Risiko, grösster Langzeitnutzen)

7. **D-1 – `ConfirmationContext` injizierbar machen**: Grösster Aufwand, grösster Gewinn für
   Testbarkeit der Write-Tools.
8. **D-2 – `BatchConfirmationService`-Interface**: Entkoppelt `OrchestrationService` von
   statischen ThreadLocal-Kontexten.
9. **O-1 – ContextProvider-Registry**: Macht das Kontext-System erweiterbar ohne Code-Änderungen.

---

## Was bereits gut gelöst ist ✅

- **LLM-Adapter-Factory-Registry** (`LlmAdapterFactory` + `LlmConfig`): Vorbildliches OCP
- **`Tool`-Plugin-Architektur**: Neue Tools erfordern nur eine neue Bean
- **`JiraWriteExecutor` mit Strategy-Pattern**: `JiraOperation`-Implementierungen klar getrennt
- **`ConfigBeans` + schmale Konfigurations-Interfaces**: Gutes DIP für Konfiguration
- **Frontend-Modulaufteilung**: `chat-renderer.js` / `chat-stream.js` / `chat.js` sauber getrennt
- **`ConfirmationFutureManager`, `CancellationManager`, `HistoryManager`**: SRP in Session-Layer

---

**Dokumentversion:** 2.0
**Erstellt:** 2026-06-29
**Ersetzt:** v1.0 vom 2026-06-26

