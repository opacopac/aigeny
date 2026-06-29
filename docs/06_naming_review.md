# Naming Review – Paket- und Dateinamen-Analyse

> Erstellt: 2026-06-29  
> Scope: `src/main/java`, `src/test/java`, `src/main/resources/static/js`

---

## Zusammenfassung

Die Gesamtstruktur ist sauber und logisch aufgebaut. Fast alle Dateien sind korrekt benannt und in passenden Paketen untergebracht. Tests spiegeln exakt die Produktionsstruktur wider. Die folgenden Punkte sind Verbesserungsvorschläge, geordnet nach Priorität.

---

## 🔴 Kritisch

### 1. Paket `llm_tool` verwendet Underscore

**Problem:** Java-Namenskonventionen schreiben vor, dass Paketnamen ausschließlich Kleinbuchstaben ohne Sonderzeichen enthalten dürfen. Das Paket `com.tschanz.aigeny.llm_tool` (und alle Sub-Pakete) verletzt diese Konvention.

**Betroffen:**

| Aktuell | Empfehlung |
|---------|------------|
| `llm_tool` | `llmtool` |
| `llm_tool.bitbucket` | `llmtool.bitbucket` |
| `llm_tool.db` | `llmtool.db` |
| `llm_tool.jira` | `llmtool.jira` |
| `llm_tool.jira.http` | `llmtool.jira.http` |
| `llm_tool.jira.operation` | `llmtool.jira.operation` |
| `llm_tool.jira.service` | `llmtool.jira.service` |

---

## 🟡 Signifikant

### 2. `LlmConfig.java` vs. `LlmConfiguration.java` – Namensverwechslungsgefahr

**Problem:** Im Paket `config` gibt es zwei sehr ähnlich klingende Typen mit grundlegend unterschiedlicher Rolle:

| Datei | Typ | Rolle |
|-------|-----|-------|
| `LlmConfiguration.java` | Interface | Read-only-View der LLM-Properties (wie `DbConfiguration`, `JiraConfiguration`, `BitbucketConfiguration`) |
| `LlmConfig.java` | `@Configuration`-Klasse | Bean-Factory, die den `LlmClient`-Bean erstellt |

`LlmConfig` liest sich wie ein weiteres Configuration-Interface, ist aber tatsächlich eine Bean-Factory-Konfigurationsklasse.

**Empfehlung:** `LlmConfig.java` → `LlmClientConfig.java` oder `LlmAdapterConfig.java`

---

## 🟡 Minor

### 3. `Messages.java` liegt im Root-Paket

**Problem:** `Messages.java` ist eine reine Utility-Klasse (lädt `messages.properties` via `ResourceBundle`). Sie liegt im Root-Paket `com.tschanz.aigeny` direkt neben `AigenyApplication.java`, obwohl sie keine Anwendungs-Einstiegspunkt-Logik enthält.

**Empfehlung:** Verschieben in ein Sub-Paket, z. B.:
- `com.tschanz.aigeny.util`
- `com.tschanz.aigeny.support`
- `com.tschanz.aigeny.i18n`

---

### 4. `ConfirmationContext.java` liegt im falschen Paket

**Problem:** `ConfirmationContext.java` liegt in `llm_tool.jira`, wird aber aktiv von höheren Schichten genutzt:
- `web`: `ConfirmationOrchestrator`, `ExecutionContextManager`, `ChatStreamingService`
- `orchestration`: diverse Stellen

Es handelt sich um ein übergreifendes ThreadLocal-Koordinationsobjekt – keine reine Tool-interne Klasse. Das analoge `BatchConfirmationContext.java` liegt bereits korrekt in `orchestration`.

**Empfehlung:** `ConfirmationContext.java` nach `orchestration` verschieben (analog zu `BatchConfirmationContext`) – oder zumindest nach `web`.

---

## 🟢 Sehr Minor

### 5. Frontend JS: Kommentar-Mismatch in `jira-write-mode.js`

**Problem:** Die Datei heißt `jira-write-mode.js`, aber der Header-Kommentar nennt einen veralteten Namen:

```js
/**
 * write-mode.js – Jira Write-Mode Toggle
 ```

Filename, CSS-Datei (`jira-write-mode.css`), Test (`jira-write-mode.test.js`) und der Import in `app.js` sind alle korrekt – nur der Datei-Docstring ist veraltet.

**Empfehlung:** Kommentar korrigieren zu `jira-write-mode.js – Jira Write-Mode Toggle`.

---

### 6. Testname `ToolGetCallDescriptionTest.java`

**Problem:** Die Datei testet, ob alle Tools `AbstractTool` korrekt implementieren und ob `getCallDescription(...)` funktioniert. Das „Get"-Präfix im Testnamen ist unüblich für Java-Testnamen (entspricht eher einem Getter-Naming-Stil als einem Behaviour-beschreibenden Testnamen).

**Empfehlung:** Umbenennen in `ToolCallDescriptionTest.java` oder `AbstractToolContractTest.java`.

---

## Gesamtübersicht

| Priorität | Datei / Paket | Problem |
|-----------|---------------|---------|
| 🔴 Kritisch | `llm_tool` (und Sub-Pakete) | Underscore im Java-Paketnamen |
| 🟡 Signifikant | `LlmConfig.java` | Namensverwechslungsgefahr mit `LlmConfiguration.java` |
| 🟡 Minor | `Messages.java` | Liegt im Root-Paket statt in einem Utility-Sub-Paket |
| 🟡 Minor | `ConfirmationContext.java` | Liegt in `llm_tool.jira`, wird aber von `web`/`orchestration` genutzt |
| 🟢 Sehr Minor | `jira-write-mode.js` | Datei-Docstring nennt `write-mode.js` statt `jira-write-mode.js` |
| 🟢 Sehr Minor | `ToolGetCallDescriptionTest.java` | „Get"-Präfix im Testnamen unüblich |

