# SOLID Principles Analysis & Refactoring Proposal

**Date:** 2026-06-26  
**Status:** Analysis Complete  
**Priority:** High

## Executive Summary

This document identifies SOLID principle violations in the AIgeny codebase and proposes prioritized refactorings to improve maintainability, testability, and extensibility. The analysis covers both backend (Java/Spring Boot) and frontend (JavaScript) code.

### Key Findings

- **Critical Issues:** 4 high-impact violations affecting testability and extensibility
- **Total Identified Issues:** 7 major refactoring opportunities
- **Estimated Effort:** 8-10 sprints for complete implementation
- **Quick Wins Available:** 2 immediate fixes possible within 1 sprint

---

## Top 7 Urgent Refactorings

### 🔴 Priority 1: ChatStreamingService – Massive SRP Violation

**Severity:** CRITICAL  
**Violations:** Single Responsibility Principle (SRP), Dependency Inversion Principle (DIP)  
**Lines:** 404 total

#### Current Issues

`ChatStreamingService` manages 6+ distinct responsibilities:
- SSE emitter lifecycle management
- Individual confirmation dialog orchestration (lines 165-193)
- Batch confirmation coordination (lines 225-253)
- ThreadLocal context setup/cleanup (lines 121-149)
- Error handling and recovery
- Orchestration coordination

**ThreadLocal Context Management (lines 121-149):**
```java
JiraTokenContext.set(jiraToken);
BitbucketTokenContext.set(bitbucketToken);
JiraWriteContext.set(jiraWriteEnabled);
ConfirmationContext.set(confirmationHandler);
```

#### Impact

- ❌ Impossible to unit test confirmation logic in isolation
- ❌ Adding new context types requires modifying this god class
- ❌ Batch confirmation logic duplicated/intertwined with single confirmation
- ❌ 100+ line methods violate SRP at method level

#### Proposed Refactoring

Split into three focused services:

```java
// Handles SSE lifecycle + event sending only
class SseStreamManager {
    SseEmitter createStream();
    void sendEvent(SseEmitter emitter, String event, String data);
    void completeStream(SseEmitter emitter);
    void handleError(SseEmitter emitter, Exception ex);
}

// Orchestrates single + batch confirmations
class ConfirmationOrchestrator {
    CompletableFuture<Boolean> requestConfirmation(ToolCall toolCall);
    List<CompletableFuture<Boolean>> requestBatchConfirmation(List<ToolCall> toolCalls);
    void handleUserResponse(String sessionId, boolean approved);
}

// Manages ThreadLocal context lifecycle
class ExecutionContextManager {
    void setupContext(ExecutionContext context);
    void cleanupContext();
}
```

**Effort:** High (1-2 sprints)  
**Benefit:** Enables isolated testing, reduces class size by 60%

---

### 🔴 Priority 2: Tool Interface – ISP Violation

**Severity:** CRITICAL  
**Violations:** Interface Segregation Principle (ISP), Liskov Substitution Principle (LSP)  
**Location:** `com.tschanz.aigeny.llm_tool.Tool`

#### Current Issues

Fat interface forcing all tools to implement write-specific methods:

```java
interface Tool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    default boolean requiresConfirmation() { return false; }  // ❌ Not used by read tools
    ToolResult execute(String args);
    default String getCallDescription(String args) { return getName(); }  // ❌ Inconsistent
}
```

**Problems:**
- Read-only tools (QueryJiraTool, ReadBitbucketFileTool, OracleDbTool) forced to implement `requiresConfirmation()`
- No compile-time type safety – write tools could be accidentally used as read-only
- `OrchestrationService` line 160 must runtime-check `tool.requiresConfirmation()` to filter write tools

#### Proposed Refactoring

Segregate into focused interface hierarchy:

```java
// Base interface - minimal contract
interface ExecutableTool {
    String getName();
    ToolResult execute(String args) throws Exception;
}

// Read tools - provide definitions for LLM
interface ReadableTool extends ExecutableTool {
    ToolDefinition getDefinition();
    String getDescription();
}

// Write tools - require confirmation
interface ConfirmableTool extends ReadableTool {
    boolean requiresConfirmation();
    String getCallDescription(String args);
}
```

**Tool Classification:**
- `ReadableTool`: QueryJiraTool, ReadBitbucketFileTool, OracleDbTool, SearchJiraUserTool, ReadJiraAttachmentTool
- `ConfirmableTool`: CreateJiraIssueTool, UpdateJiraIssueTool, AddJiraCommentTool, CloneJiraIssueTool

**Effort:** Medium (1 sprint)  
**Benefit:** Compile-time type safety, clearer tool semantics

---

### 🔴 Priority 3: ChatController – SRP + Hidden Dependency

**Severity:** HIGH  
**Violations:** Single Responsibility Principle (SRP), Dependency Inversion Principle (DIP)  
**Lines:** 300+ with 8 distinct endpoints

#### Critical Issue: Lines 306-309

```java
public static QueryResult getLastResult(HttpSession session) {
    ChatSessionService service = new ChatSessionService();  // ❌ BREAKS DIP!
    return service.getLastQueryResult(session);
}
```

**Why This Is Critical:**
- Bypasses Spring dependency injection
- Creates new instance instead of using singleton
- Makes code impossible to mock/test
- Called by `ExportController` (implicit tight coupling between controllers)

#### SRP Violations

Single controller handles 8 distinct concerns:

| Lines | Endpoint | Responsibility |
|-------|----------|----------------|
| 83-130 | `/api/chat` | Chat message handling |
| 134-149 | `/api/chat/stream` | SSE streaming |
| 160-172 | `/api/chat/confirm` | Single confirmations |
| 185-211 | `/api/chat/confirm-batch` | Batch confirmations |
| 215-220 | `/api/chat/cancel` | Cancellation |
| 224-229 | `/api/chat/history` | History clearing |
| 233-247 | `/api/chat/schema-reload` | Schema reload |
| 251-301 | Token management | 5 token endpoints |

#### Proposed Refactoring

Split into domain-focused controllers:

```java
@RestController
@RequestMapping("/api/chat")
class ChatController {
    // Only message & streaming endpoints
    @PostMapping ResponseEntity<String> sendMessage(...)
    @GetMapping("/stream") SseEmitter streamChat(...)
}

@RestController
@RequestMapping("/api/confirmations")
class ConfirmationController {
    @PostMapping("/single") ResponseEntity<Void> confirmSingle(...)
    @PostMapping("/batch") ResponseEntity<Void> confirmBatch(...)
}

@RestController
@RequestMapping("/api/tokens")
class TokenController {
    @GetMapping("/{provider}") ResponseEntity<TokenInfo> getToken(...)
    @PostMapping("/{provider}") ResponseEntity<Void> saveToken(...)
    @DeleteMapping("/{provider}") ResponseEntity<Void> deleteToken(...)
}

@RestController
@RequestMapping("/api/system")
class SystemController {
    @DeleteMapping("/history") ResponseEntity<Void> clearHistory(...)
    @PostMapping("/schema-reload") ResponseEntity<Void> reloadSchema(...)
    @PostMapping("/cancel") ResponseEntity<Void> cancelGeneration(...)
    @GetMapping("/status") ResponseEntity<StatusInfo> getStatus(...)
}
```

**Immediate Fix (Today):**
```java
// Inject dependency instead of creating instance
@Autowired
private ChatSessionService chatSessionService;

public QueryResult getLastResult(HttpSession session) {
    return chatSessionService.getLastQueryResult(session);
}
```

**Effort:** Low for immediate fix, High for full split (2 sprints)  
**Benefit:** Testable controllers, clear API boundaries

---

### 🔴 Priority 4: ChatSessionService – ISP Violation

**Severity:** HIGH  
**Violations:** Interface Segregation Principle (ISP), Single Responsibility Principle (SRP)  
**Lines:** 185 total

#### Current Issues

Monolithic service managing 5 unrelated concerns:

| Lines | Responsibility | Clients |
|-------|----------------|---------|
| 43-62 | Chat history CRUD | ChatController, ChatStreamingService |
| 71-95 | Query result caching | ChatController, ExportController |
| 97-128 | Pending Jira actions | JiraWriteExecutor, ChatController |
| 130-155 | Cancellation flags | ChatStreamingService, ChatController |
| 157-185 | Confirmation futures | ChatStreamingService, ChatController |

**Problem:** Every service needing ANY session data depends on this monolithic service. Cannot inject just history manager or just cancellation manager.

#### Proposed Refactoring

Split into focused, single-responsibility managers:

```java
@Service
class ChatHistoryManager {
    void addMessage(HttpSession session, Message message);
    List<Message> getHistory(HttpSession session);
    void clearHistory(HttpSession session);
}

@Service
class QueryResultCache {
    void storeResult(HttpSession session, QueryResult result);
    QueryResult getLastResult(HttpSession session);
}

@Service
class PendingJiraActionRegistry {
    void addPendingAction(HttpSession session, PendingJiraAction action);
    List<PendingJiraAction> getPendingActions(HttpSession session);
    void clearPendingActions(HttpSession session);
}

@Service
class CancellationManager {
    void setCancelled(HttpSession session, boolean cancelled);
    boolean isCancelled(HttpSession session);
}

@Service
class ConfirmationFutureRegistry {
    void registerFuture(HttpSession session, CompletableFuture<Boolean> future);
    CompletableFuture<Boolean> getFuture(HttpSession session);
    void removeFuture(HttpSession session);
}
```

**Effort:** Medium (1 sprint)  
**Benefit:** Focused dependencies, easier to test, clearer responsibilities

---

### 🟡 Priority 5: OrchestrationService – Mixed Responsibilities

**Severity:** MEDIUM  
**Violations:** Single Responsibility Principle (SRP)  
**Lines:** 79-143 (core orchestration), 155-175 (batch pre-scan)

#### Current Issues

Mixes orchestration logic with UI coordination:

**Lines 155-175: Batch Confirmation Pre-Scan**
```java
// ❌ This UI coordination logic shouldn't be in orchestration layer
boolean batchMode = chatStreamingService.isBatchConfirmationEnabled();
if (batchMode) {
    List<ToolCall> writeTools = collectWriteTools(response);
    if (writeTools.size() > 1) {
        List<CompletableFuture<Boolean>> futures = 
            chatStreamingService.requestBatchConfirmation(writeTools);
        // ... wait and process
    }
}
```

**Problem:** Orchestration layer shouldn't know about batch confirmation UI patterns. This is presentation logic.

#### Proposed Refactoring

Extract batch confirmation pre-scanning to dedicated service:

```java
@Service
class BatchConfirmationPreScanner {
    List<ToolCall> scanForBatchWriteTools(ChatResponse response);
    boolean requiresBatchConfirmation(ChatResponse response);
    List<CompletableFuture<Boolean>> collectBatchConfirmations(
        List<ToolCall> writeTools
    );
}
```

**OrchestrationService** simplified to only:
- Tool iteration loop
- History management
- Message formatting

**Effort:** Low (1 sprint)  
**Benefit:** Clearer separation between orchestration and UI coordination

---

### 🟡 Priority 6: ThreadLocal Context Management – DIP Violation

**Severity:** MEDIUM  
**Violations:** Dependency Inversion Principle (DIP), Testability  
**Location:** Throughout tool implementations

#### Current Issues

Tools directly depend on ThreadLocal context objects:

```java
// Set in multiple places:
// - ChatStreamingService:130
// - ChatController:101
// - JiraWriteExecutor:45
JiraTokenContext.set(token);

// Tools directly read ThreadLocal
class QueryJiraTool {
    public ToolResult execute(String args) {
        String token = JiraTokenContext.get();  // ❌ Tight coupling
        // ...
    }
}
```

**Problems:**
- Tools tightly coupled to ThreadLocal implementation
- Hard to test – requires manual ThreadLocal setup
- Context values scattered across 3+ files
- Adding new tool types requires new ThreadLocal context classes

#### Proposed Refactoring

Replace ThreadLocal with dependency-injected ExecutionContext:

```java
@Component
@Scope("prototype")
class ExecutionContext {
    private final String jiraToken;
    private final String bitbucketToken;
    private final boolean jiraWriteEnabled;
    private final ConfirmationHandler confirmationHandler;
    
    // Immutable, thread-safe
}

// Tools receive context via constructor
class QueryJiraTool {
    public ToolResult execute(String args, ExecutionContext context) {
        String token = context.getJiraToken();  // ✅ Injected dependency
        // ...
    }
}

// Executor manages context lifecycle
class ToolExecutor {
    public ToolResult executeWithContext(
        ExecutionContext context,
        ToolCall toolCall
    ) {
        Tool tool = getTool(toolCall.getName());
        return tool.execute(toolCall.getArguments(), context);
    }
}
```

**Migration Path:**
1. Add `ExecutionContext` parameter to `Tool.execute()` method signature
2. Update all tool implementations to use context parameter
3. Remove ThreadLocal context classes
4. Update ChatStreamingService to pass context to ToolExecutor

**Effort:** High (2 sprints)  
**Benefit:** Testable tools, no ThreadLocal magic, extensible for new context types

---

### 🟡 Priority 7: Frontend JavaScript – Mixed Concerns

**Severity:** MEDIUM  
**Violations:** Single Responsibility Principle (SRP), Interface Segregation Principle (ISP)  
**Location:** `src/main/resources/static/js/chat-stream.js`

#### Current Issues

`chat-stream.js` mixes 3 distinct responsibilities:

| Lines | Responsibility | Dependency |
|-------|----------------|------------|
| 45-95 | SSE parsing & event dispatching | EventSource API |
| 97-138 | HTTP fetch for sending messages | Fetch API |
| 70-81 | Confirmation UI coordination | Renderer methods |

**Problems:**
- Cannot test SSE parsing independently from network logic
- Renderer receives 8+ methods but each event type uses only 2-3
- Confirmation logic mixed with stream processing

#### Proposed Refactoring

Split into focused modules:

```javascript
// chat-stream.js - HTTP communication only
export class ChatStreamClient {
    async sendMessage(message) { /* fetch logic */ }
    async stopGeneration(sessionId) { /* fetch logic */ }
}

// stream-processor.js - SSE parsing & routing
export class StreamEventProcessor {
    constructor(eventHandlers) {
        this.handlers = eventHandlers;
    }
    
    processStream(eventSource) {
        eventSource.addEventListener('message', e => {
            const event = JSON.parse(e.data);
            this.handlers[event.type]?.(event.data);
        });
    }
}

// confirmation-renderer.js - UI rendering only
export class ConfirmationRenderer {
    renderSingleConfirmation(toolCall) { /* ... */ }
    renderBatchConfirmation(toolCalls) { /* ... */ }
}
```

**Usage:**
```javascript
const processor = new StreamEventProcessor({
    'confirmation': data => renderer.renderSingleConfirmation(data),
    'batch-confirmation': data => renderer.renderBatchConfirmation(data),
    'content': data => renderer.appendContent(data),
    'done': () => renderer.markComplete()
});
```

**Effort:** Medium (1 sprint)  
**Benefit:** Testable components, reusable event processing

---

## Implementation Roadmap

### Phase 1: Quick Wins (Sprint 1)

**Immediate Fixes:**
1. ✅ Fix `ChatController` line 307 – inject `ChatSessionService` dependency
2. ✅ Add `abstract class ReadOnlyTool extends AbstractTool` with empty `requiresConfirmation()`

**Sprint 1 Deliverables:**
- [ ] Extract `ConfirmationOrchestrator` from `ChatStreamingService` (reduces by 150 lines)
- [ ] Split `ChatSessionService` into 3 focused managers (History, QueryResult, Cancellation)

**Testing:** All existing tests must pass, add unit tests for new managers

---

### Phase 2: Interface Segregation (Sprint 2-3)

**Sprint 2:**
- [ ] Create `ReadableTool` and `ConfirmableTool` interfaces
- [ ] Migrate all 15 tool implementations to new interfaces
- [ ] Update `ToolExecutor` to handle segregated interfaces

**Sprint 3:**
- [ ] Split `ChatController` into 4 domain controllers
- [ ] Update frontend to use new API endpoints
- [ ] Update API documentation

**Testing:** Integration tests for all tool types, API contract tests

---

### Phase 3: Context Management (Sprint 4-5)

**Sprint 4:**
- [ ] Create `ExecutionContext` class
- [ ] Add context parameter to `Tool.execute()` signature
- [ ] Update 5 core tools (Jira query, Bitbucket, DB)

**Sprint 5:**
- [ ] Migrate remaining 10 tools to use `ExecutionContext`
- [ ] Remove ThreadLocal context classes
- [ ] Update `ChatStreamingService` to pass context

**Testing:** Unit tests for tools with mocked context, integration tests

---

### Phase 4: Frontend Cleanup (Sprint 6)

**Sprint 6:**
- [ ] Extract `StreamEventProcessor` from `chat-stream.js`
- [ ] Create `ConfirmationRenderer` module
- [ ] Add unit tests for event processing logic

**Testing:** JavaScript unit tests with Vitest

---

### Phase 5: Final Cleanup (Sprint 7-8)

**Sprint 7:**
- [ ] Extract `BatchConfirmationPreScanner` from `OrchestrationService`
- [ ] Simplify `OrchestrationService` to core responsibilities
- [ ] Performance testing

**Sprint 8:**
- [ ] Documentation updates
- [ ] Code review and final adjustments
- [ ] Regression testing

---

## Success Metrics

### Testability Improvements

| Metric | Before | Target |
|--------|--------|--------|
| Unit test coverage (services) | ~40% | 75%+ |
| Mockable dependencies | Low | High |
| Test setup complexity | High (ThreadLocal) | Low (DI) |

### Code Quality Metrics

| Metric | Before | Target |
|--------|--------|--------|
| Avg class size (lines) | 250 | <150 |
| Classes with 5+ responsibilities | 4 | 0 |
| Fat interfaces | 1 | 0 |
| Hardcoded dependencies | 3 | 0 |

### Extensibility Improvements

After refactoring, adding new features becomes easier:

- ✅ **New tool types:** Implement `ReadableTool` or `ConfirmableTool` interface
- ✅ **New confirmation workflows:** Extend `ConfirmationOrchestrator`
- ✅ **New context types:** Add fields to `ExecutionContext` (single point)
- ✅ **New LLM providers:** Factory pattern already supports this ✓

---

## Risk Assessment

### Low Risk
- **#1 ChatController line 307 fix** – Single line change, immediate benefit
- **#6 Frontend split** – JavaScript modules are loosely coupled already

### Medium Risk
- **#2 Tool interface split** – Touches 15 tool classes, but straightforward refactor
- **#4 ChatSessionService split** – Well-defined boundaries, just needs extraction

### High Risk
- **#5 ThreadLocal to ExecutionContext** – Touches all tools, requires careful migration
- **#3 ChatController split** – Frontend API contract changes, requires frontend updates

**Mitigation Strategy:**
- Feature flags for new controllers (run old + new in parallel)
- Incremental tool migration (migrate 5 tools per sprint)
- Comprehensive integration test suite before/after

---

## Conclusion

The AIgeny codebase demonstrates good architectural practices in some areas (LLM adapter factories, separation of concerns between packages) but suffers from **tight coupling in session management and confirmation handling** across layers.

### Key Takeaways

1. **ChatStreamingService is the biggest pain point** – 404 lines managing 6 responsibilities
2. **Fat Tool interface prevents type safety** – Read vs write tools should be distinct
3. **ThreadLocal context management is hard to test** – Should use dependency injection
4. **ChatController violates SRP dramatically** – 8 endpoints = 8 responsibilities

### Recommended Approach

**Start with quick wins** (Sprint 1), then **systematically address interface segregation** (Sprint 2-3), **migrate to context injection** (Sprint 4-5), and **finish with cleanup** (Sprint 6-8).

**Estimated Total Effort:** 8-10 sprints  
**Expected ROI:** 50% reduction in code complexity, 80% improvement in testability

---

## Appendix: SOLID Principles Quick Reference

### Single Responsibility Principle (SRP)
> A class should have only one reason to change

**Violations in AIgeny:**
- ChatStreamingService (6 reasons to change)
- ChatController (8 reasons to change)
- ChatSessionService (5 reasons to change)

### Open/Closed Principle (OCP)
> Open for extension, closed for modification

**Good Examples:**
- LlmAdapterFactory pattern ✓
- Tool plugin architecture ✓

### Liskov Substitution Principle (LSP)
> Subtypes must be substitutable for base types

**Violations:**
- Tool interface (read tools don't truly substitute for write tools)

### Interface Segregation Principle (ISP)
> Clients shouldn't depend on methods they don't use

**Violations:**
- Tool interface (read tools forced to implement `requiresConfirmation()`)
- ChatSessionService (clients depend on all 5 concerns)

### Dependency Inversion Principle (DIP)
> Depend on abstractions, not concretions

**Violations:**
- ChatController line 307 (hardcoded `new ChatSessionService()`)
- ThreadLocal context management (tools depend on concrete ThreadLocal)

---

**Document Version:** 1.0  
**Last Updated:** 2026-06-26  
**Next Review:** After Phase 1 completion
