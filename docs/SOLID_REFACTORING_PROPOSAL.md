# SOLID-Prinzipien Refactoring-Vorschläge für AIgeny

## Zusammenfassung

Diese Analyse identifiziert Verletzungen der SOLID-Prinzipien im AIgeny-Codebase und schlägt konkrete Refactorings vor.

## 🔴 KRITISCH - Höchste Priorität

### 1. ChatController - Single Responsibility Principle Verletzung

**Aktueller Zustand:** 506 Zeilen, ~15 verschiedene Verantwortlichkeiten

**Problem:**
- HTTP-Request-Handling
- Session-Management (History, Tokens, Cancel-Flags)
- Chat-Orchestrierung
- Jira-Token-Management
- Bitbucket-Token-Management
- SSE-Streaming
- Asynchrone Verarbeitung
- Export-Koordination
- Status-Aggregation

**Refactoring-Vorschlag:**

```
ChatController (nur HTTP-Endpoints)
├── ChatSessionService (Session-Management)
│   ├── manages chat history
│   ├── manages query results
│   └── manages cancellation flags
├── TokenService (Token-Management)
│   ├── manages Jira tokens (user vs. config)
│   ├── manages Bitbucket tokens
│   └── provides effective token resolution
├── ChatStreamingService (SSE-Streaming-Logik)
│   ├── handles SSE emitter lifecycle
│   ├── sends tool call events
│   └── sends intermediate messages
└── StatusAggregatorService (Status-Aggregation)
    ├── aggregates DB status
    ├── aggregates Jira status
    └── aggregates Bitbucket status
```

**Geschätzter Aufwand:** 8-12 Stunden

---

### 2. AigenyProperties - SRP-Verletzung (Configuration + Business Logic)

**Aktueller Zustand:** 170 Zeilen, Configuration + Secret-File-Resolving + Validation

**Problem:**
- Configuration-Holder
- Secret-File-Resolution (@PostConstruct mit I/O)
- Business-Logic für "isConfigured"-Checks
- Nested classes für verschiedene Konfigurationsbereiche

**Refactoring-Vorschlag:**

```java
// Reine Konfiguration
@ConfigurationProperties(prefix = "aigeny")
public class AigenyProperties {
    private LlmProperties llm;
    private DbProperties db;
    private JiraProperties jira;
    private BitbucketProperties bitbucket;
    // nur Getters/Setters
}

// Separate Service für Secret-Resolution
@Service
public class SecretFileResolver {
    public void resolveSecrets(AigenyProperties props) { ... }
}

// Separate Service für Configuration-Validation
@Service
public class ConfigurationValidator {
    public boolean isDbConfigured(DbProperties db) { ... }
    public boolean isJiraConfigured(JiraProperties jira) { ... }
    public boolean isBitbucketConfigured(BitbucketProperties bb) { ... }
}
```

**Geschätzter Aufwand:** 4-6 Stunden

---

### 3. GitHubCopilotService - SRP-Verletzung (383 Zeilen)

**Aktueller Zustand:** OAuth, Token-Management, Persistence, API-Calls, Threading

**Problem:**
- OAuth Device Flow Orchestration
- Token Storage & Persistence (File I/O)
- Token Refresh Logic
- HTTP API Communication
- Background Thread Management
- Model Listing

**Refactoring-Vorschlag:**

```
GitHubCopilotService (Facade)
├── GitHubOAuthClient (OAuth-Flow)
│   ├── startDeviceFlow()
│   └── pollForToken()
├── GitHubTokenStore (Persistence)
│   ├── load()
│   ├── save()
│   └── delete()
├── CopilotSessionManager (Token Refresh)
│   ├── getCopilotToken()
│   └── refreshIfNeeded()
└── CopilotApiClient (HTTP API)
    ├── listModels()
    └── getCommonHeaders()
```

**Geschätzter Aufwand:** 6-10 Stunden

---

## 🟡 WICHTIG - Mittlere Priorität

### 4. LlmConfig - Open/Closed Principle Verletzung

**Aktueller Zustand:**
```java
return switch (provider.toLowerCase()) {
    case "claude"         -> new AnthropicAdapter(props);
    case "github-copilot" -> new GitHubCopilotAdapter(props, github);
    default               -> new OpenAiCompatibleAdapter(props);
};
```

**Problem:**
- Jeder neue LLM-Provider erfordert Code-Änderung
- Switch-Statement ist closed für Extension

**Refactoring-Vorschlag:**

```java
// Strategy Pattern mit Factory Registry
public interface LlmAdapterFactory {
    String getProviderName();
    LlmClient createAdapter(AigenyProperties props, GitHubCopilotService github);
    boolean supports(String providerName);
}

@Component
public class AnthropicAdapterFactory implements LlmAdapterFactory {
    @Override
    public String getProviderName() { return "claude"; }
    
    @Override
    public LlmClient createAdapter(AigenyProperties props, GitHubCopilotService github) {
        return new AnthropicAdapter(props);
    }
    
    @Override
    public boolean supports(String providerName) {
        return "claude".equalsIgnoreCase(providerName);
    }
}

@Configuration
public class LlmConfig {
    @Bean
    public LlmClient llmClient(AigenyProperties props, 
                               GitHubCopilotService github,
                               List<LlmAdapterFactory> factories) {
        String provider = props.getLlm().getProvider();
        return factories.stream()
            .filter(f -> f.supports(provider))
            .findFirst()
            .map(f -> f.createAdapter(props, github))
            .orElse(new OpenAiCompatibleAdapter(props)); // Fallback
    }
}
```

**Geschätzter Aufwand:** 3-4 Stunden

---

### 5. JiraWriteExecutor - SRP-Verletzung (238 Zeilen)

**Aktueller Zustand:** HTTP-Logic + Business-Logic für 3 verschiedene Operationen

**Problem:**
- Ausführt UPDATE, CREATE, COMMENT - verschiedene Operationen
- HTTP-Kommunikation gemischt mit Business-Logic
- Subtask-Erstellung gemischt in create()
- Clone-Link-Erstellung gemischt in create()

**Refactoring-Vorschlag:**

```java
// Strategie-Pattern
public interface JiraOperation {
    String execute(PendingJiraAction action, String baseUrl, String token) throws Exception;
}

@Component
public class UpdateIssueOperation implements JiraOperation { ... }

@Component
public class AddCommentOperation implements JiraOperation { ... }

@Component
public class CreateIssueOperation implements JiraOperation {
    private final SubtaskCreationService subtaskService;
    private final IssueLinkService linkService;
    private final JiraHttpClient httpClient;
    // ...
}

@Service
public class JiraWriteExecutor {
    private final Map<ActionType, JiraOperation> operations;
    
    public String execute(PendingJiraAction action, String token) {
        JiraOperation operation = operations.get(action.getActionType());
        return operation.execute(action, baseUrl, token);
    }
}
```

**Geschätzter Aufwand:** 5-7 Stunden

---

### 6. OrchestrationService - Zu viele Verantwortlichkeiten

**Aktueller Zustand:** 171 Zeilen, Chat-Loop + Tool-Execution + Prompt-Building

**Problem:**
- Agentic Tool-Call Loop
- Tool Discovery & Execution
- System Prompt Building
- Message History Management
- Cancellation Handling

**Refactoring-Vorschlag:**

```java
@Service
public class OrchestrationService {
    private final ToolExecutor toolExecutor;
    private final PromptBuilder promptBuilder;
    private final MessageHistoryManager historyManager;
    
    public ChatResult chat(...) {
        // Orchestriert nur, delegiert Details
    }
}

@Service
public class ToolExecutor {
    private final List<Tool> tools;
    
    public ToolResult execute(ToolCall toolCall) { ... }
    public Tool findTool(String name) { ... }
}

@Service
public class PromptBuilder {
    public String buildSystemPrompt() { ... }
}
```

**Geschätzter Aufwand:** 4-6 Stunden

---

## 🟢 OPTIONAL - Niedrige Priorität

### 7. Dependency Inversion - AigenyProperties überall verwendet

**Problem:**
- Viele Klassen hängen direkt von `AigenyProperties` ab (konkrete Klasse)
- Besser: Interfaces für spezifische Konfigurationsbereiche

**Refactoring-Vorschlag:**

```java
public interface LlmConfiguration {
    String getProvider();
    String getApiKey();
    String getBaseUrl();
    String getModel();
}

public interface DbConfiguration {
    String getUrl();
    String getUsername();
    String getPassword();
    String getEffectiveSchema();
}

// AigenyProperties implementiert diese Interfaces
@Component
@ConfigurationProperties(prefix = "aigeny")
public class AigenyProperties implements LlmConfiguration, DbConfiguration, ... {
    // ...
}

// Services verwenden die Interfaces
public class OpenAiCompatibleAdapter implements LlmClient {
    private final LlmConfiguration config;
    
    public OpenAiCompatibleAdapter(LlmConfiguration config) {
        this.config = config;
    }
}
```

**Geschätzter Aufwand:** 6-8 Stunden

---

### 8. Tool-Interface - Kleinere Verbesserungen

**Aktueller Zustand:** Interface mit static ObjectMapper

```java
public interface Tool {
    ObjectMapper _TOOL_JSON = new ObjectMapper();
    // ...
}
```

**Problem:**
- Static-Member in Interface ist unüblich
- Tight Coupling an Jackson

**Refactoring-Vorschlag:**

```java
public interface Tool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    ToolResult execute(String argumentsJson) throws Exception;
    String getCallDescription(String argumentsJson);
}

@Component
public abstract class AbstractTool implements Tool {
    protected final ObjectMapper objectMapper;
    
    protected AbstractTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getCallDescription(String argumentsJson) {
        // Gemeinsame Default-Implementierung
    }
}
```

**Geschätzter Aufwand:** 2-3 Stunden

---

## Priorisierungs-Empfehlung

### Phase 1 (Kritisch - sofort angehen):
1. **ChatController splitten** → Höchste Komplexität, größter Impact
2. **AigenyProperties bereinigen** → Betrifft viele Klassen
3. **GitHubCopilotService aufteilen** → Hohe Komplexität

**Gesamtaufwand Phase 1:** ~18-28 Stunden

### Phase 2 (Wichtig - mittelfristig):
4. **LlmConfig auf OCP umstellen** → Bessere Erweiterbarkeit
5. **JiraWriteExecutor refactoren** → Klarere Struktur
6. **OrchestrationService splitten** → Bessere Testbarkeit

**Gesamtaufwand Phase 2:** ~12-17 Stunden

### Phase 3 (Optional - langfristig):
7. **DIP für Configurations** → Sauberere Abhängigkeiten
8. **Tool-Interface verbessern** → Kleinere Verbesserungen

**Gesamtaufwand Phase 3:** ~8-11 Stunden

---

## Weitere Beobachtungen

### Positive Aspekte:
✅ Gute Verwendung von Interfaces (LlmClient, Tool)
✅ Dependency Injection konsequent verwendet
✅ Klare Package-Struktur
✅ Services sind als Spring Beans konfiguriert
✅ Logging ist gut implementiert

### Verbesserungspotential:
⚠️ Zu wenig Unit-Tests sichtbar (nur JavaScript-Tests)
⚠️ Keine klare Exception-Handling-Strategie
⚠️ ThreadLocal-Usage (Context-Klassen) - könnte problematisch sein
⚠️ Fehlende Abstraktionen für HTTP-Clients
⚠️ Magic Strings und Constants nicht immer zentralisiert

---

## Nächste Schritte

1. **Entscheidung treffen:** Welche Phase soll zuerst angegangen werden?
2. **Tests schreiben:** Vor dem Refactoring Characterization-Tests erstellen
3. **Schrittweise vorgehen:** Ein Refactoring nach dem anderen
4. **Code Reviews:** Nach jedem größeren Refactoring Review durchführen

**Fragen?** Soll ich mit einem konkreten Refactoring beginnen?

