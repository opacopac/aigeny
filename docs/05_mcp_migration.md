# MCP-Migration der Tool-Anbindungen

## Ziel

Die Tool-Integrationen von AIgeny (DB, Jira, Bitbucket, ...) sollen schrittweise auf das
**Model Context Protocol (MCP)** umgestellt werden, statt Tools als reine In-Process-Java-Klassen
zu implementieren, die direkt JDBC/HTTP-Clients aufrufen.

**Phase 1 (dieser Schritt): Oracle-DB-Tool**, als eingebetteter MCP-Server – d.h. der Server ist
Teil desselben Repos/Artefakts, wird aber bereits über das echte MCP-Protokoll angesprochen.

## Warum MCP?

- Tools werden von der konkreten LLM-Anbindung entkoppelt (Standardprotokoll statt Custom-`Tool`-Interface).
- Vorbereitung für externe/gemeinsam genutzte MCP-Server (z.B. ein zentraler Firmen-DB-MCP-Server,
  den mehrere Anwendungen nutzen), ohne den Orchestrations-Code in AIgeny anzufassen.
- Klar geschnittene Prozessgrenze: DB-Zugriff (Connection Pool, SQL-Validierung) kann unabhängig
  vom Hauptprozess laufen/aktualisiert werden.

## Architektur (Phase 1)

```
┌─────────────────────────────┐        stdio (MCP JSON-RPC)        ┌───────────────────────────────┐
│ AIgeny Hauptprozess (Spring) │ ───────────────────────────────▶  │ OracleMcpServerLauncher (Child)│
│                              │ ◀───────────────────────────────  │  - eigener Hikari-Pool          │
│ OracleMcpClientTool          │                                    │  - Tool "query_oracle_db"       │
│  implements Tool             │                                    │  - liest Env-Vars (URL/User/PW) │
└─────────────────────────────┘                                    └───────────────────────────────┘
```

- `com.tschanz.aigeny.database.mcp.OracleMcpServerLauncher`
  Eigenständige `main()`-Klasse (kein Spring-Kontext, schneller Start). Baut einen eigenen,
  kleinen HikariCP-Pool aus Umgebungsvariablen (`AIGENY_DB_URL`, `AIGENY_DB_USERNAME`,
  `AIGENY_DB_PASSWORD`, `AIGENY_DB_SCHEMA`) und registriert das Tool `query_oracle_db`
  über den offiziellen MCP Java SDK Server (`McpServer.sync(...)`, stdio-Transport).
  Enthält dieselbe SQL-Validierung (SELECT-only, Keyword-Blacklist) wie bisher.

- `com.tschanz.aigeny.database.mcp.OracleMcpClientTool`
  Spring-`@Service`, implementiert weiterhin das bestehende `Tool`-Interface (gleicher Name,
  gleiche Beschreibung, gleiches JSON-Schema wie `OracleDbTool`). Startet beim App-Start
  (`@PostConstruct`) den obigen Server als Kindprozess (`ServerParameters` + `StdioClientTransport`),
  ruft `initialize()` auf und leitet `execute()`-Aufrufe per `callTool(...)` an ihn weiter.
  Beendet den Kindprozess sauber via `closeGracefully()` (`@PreDestroy`).

- `com.tschanz.aigeny.database.OracleDbTool` (bestehend) bleibt als Direkt-Implementierung
  erhalten und ist weiterhin Standard.

### Umschalten per Property

```yaml
aigeny:
  db:
    mcp-enabled: false   # true = OracleMcpClientTool aktiv, false (Default) = OracleDbTool aktiv
```

Beide Tool-Bohnen sind über `@ConditionalOnProperty` exklusiv – es ist immer genau ein
`query_oracle_db`-Tool im `ToolExecutor` registriert. `ToolExecutor`, `OrchestrationService`
und alles, was mit der `Tool`-Schnittstelle arbeitet, bleiben unverändert.

### Datenübertragung / CSV-Export

Damit der bestehende CSV-Export weiter funktioniert, liefert der MCP-Server im
`CallToolResult` zwei Content-Blöcke:
1. Menschenlesbarer Text (identisch zu `QueryResult.toText()`), für die LLM-Antwort/den Chat.
2. Strukturiertes JSON (`{"columns": [...], "rows": [...]}`), das der Client zu einem
   `QueryResult` zusammenbaut – wie bisher.

### Achtung: gepackte Fat-JAR

`aigeny-1.0.0.jar` (Spring-Boot-Repackage) enthält seine Abhängigkeiten unter `BOOT-INF/lib/`,
sichtbar nur über Spring Boots eigenen Classloader. Ein simples `java -cp <fat-jar> MeineKlasse`
würde dort `NoClassDefFoundError` werfen. `OracleMcpClientTool#buildJavaArgs()` erkennt diesen
Fall (Classpath = genau ein `.jar`, Classloader beginnt mit `org.springframework.boot.loader`)
und startet den Kindprozess stattdessen über Spring Boots `PropertiesLauncher` mit
`-Dloader.main=com.tschanz.aigeny.database.mcp.OracleMcpServerLauncher`, damit der volle
BOOT-INF-Classpath korrekt aufgebaut wird. Im IDE-/`mvn spring-boot:run`-Betrieb (normaler,
mehrteiliger Classpath) wird weiterhin der einfache `-cp <classpath> <MainClass>`-Aufruf genutzt.

## Nächste Schritte / Ausblick (Phase 2+)

1. **Separates Artefakt**: `OracleMcpServerLauncher` in ein eigenes Maven-Modul (oder Repo)
   auslösen und als eigenständigen Prozess/Container betreiben.
2. **Transport wechseln**: Statt Kindprozess + stdio auf HTTP/SSE (`HttpClientSseClientTransport`)
   umstellen, sobald der Server remote/zentral läuft. `OracleMcpClientTool` ändert dabei nur die
   Transport-Konstruktion, nicht die `Tool`-Implementierung.
3. **Weitere Tools migrieren**: Jira- und Bitbucket-Tools nach demselben Muster
   (`XyzMcpServerLauncher` + `XyzMcpClientTool`) umstellen.
4. **Mehrere Tools pro Server**: Sobald mehr als ein Tool im selben MCP-Server sitzt (z.B. Schema-
   Reload zusätzlich zu Query), kann `McpServer.SyncSpecification#tools(...)` mit mehreren
   `McpServerFeatures.SyncToolSpecification`-Einträgen genutzt werden.
5. **Health/Status**: `SchemaLoader`/`SchemaController` könnten optional ebenfalls über den
   MCP-Client laufen, statt einen zweiten, unabhängigen Hikari-Pool zu öffnen.

## Abhängigkeit

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.10.0</version>
</dependency>
```

Offizielles Java MCP SDK (`https://github.com/modelcontextprotocol/java-sdk`), enthält
Client + Server + stdio-Transport. Die API wurde vor der Implementierung per `javap` gegen
das tatsächlich aufgelöste Jar verifiziert (kein Rätselraten anhand von Dokumentation).


