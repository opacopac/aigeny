# MCP-Migration der Tool-Anbindungen

## Ziel

Die Tool-Integrationen von AIgeny (DB, Jira, Bitbucket, ...) sollen schrittweise auf das
**Model Context Protocol (MCP)** umgestellt werden, statt Tools als reine In-Process-Java-Klassen
zu implementieren, die direkt JDBC/HTTP-Clients aufrufen.

**Status: Oracle-DB-Tool vollständig auf MCP umgestellt** (kein Feature-Flag, keine
Direkt-JDBC-Implementierung mehr im Hauptprozess). Der Server ist weiterhin Teil desselben
Repos/Artefakts, wird aber ausschliesslich über das echte MCP-Protokoll angesprochen.

## Warum MCP?

- Tools werden von der konkreten LLM-Anbindung entkoppelt (Standardprotokoll statt Custom-`Tool`-Interface).
- Vorbereitung für externe/gemeinsam genutzte MCP-Server (z.B. ein zentraler Firmen-DB-MCP-Server,
  den mehrere Anwendungen nutzen), ohne den Orchestrations-Code in AIgeny anzufassen.
- Klar geschnittene Prozessgrenze: DB-Zugriff (Connection Pool, SQL-Validierung) kann unabhängig
  vom Hauptprozess laufen/aktualisiert werden.

## Architektur

```
┌─────────────────────────────┐        stdio (MCP JSON-RPC)        ┌───────────────────────────────┐
│ AIgeny Hauptprozess (Spring) │ ───────────────────────────────▶  │ OracleMcpServerLauncher (Child)│
│                              │ ◀───────────────────────────────  │  - eigener Hikari-Pool          │
│ *Tool-Klassen (mcp_client)   │                                    │  - 5 Tools (mcp_server)         │
└─────────────────────────────┘                                    └───────────────────────────────┘
```

- `com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher`
  Eigenständige `main()`-Klasse (kein Spring-Kontext, schneller Start). Baut einen eigenen,
  kleinen HikariCP-Pool aus Umgebungsvariablen (`AIGENY_DB_URL`, `AIGENY_DB_USERNAME`,
  `AIGENY_DB_PASSWORD`, `AIGENY_DB_SCHEMA`) und registriert fünf Tools (`list_tables`,
  `describe_table`, `search_schema`, `sample_table`, `run_query`) - je eine eigene
  `OracleMcpToolHandler`-Implementierung im selben Package - über den offiziellen
  MCP Java SDK Server (`McpServer.sync(...)`, stdio-Transport). SQL-Validierung
  (SELECT-only, Keyword-Blacklist) lebt in `RunQueryHandler`.

- `com.tschanz.aigeny.database.mcp_client.*`
  Fünf Spring-`@Service`-Klassen (`ListTablesTool`, `DescribeTableTool`, `SearchSchemaTool`,
  `SampleTableTool`, `RunQueryTool`), die alle von `AbstractOracleMcpTool` erben. Diese Basisklasse
  startet beim App-Start (`@PostConstruct` in `OracleMcpConnection`) den obigen Server als
  Kindprozess (`ServerParameters` + `StdioClientTransport`), ruft `initialize()` und danach
  `listTools()` auf (Name/Beschreibung/Schema werden dynamisch vom Server übernommen statt
  hartcodiert) und leitet `execute()`-Aufrufe per `callTool(...)` weiter.
  Beendet den Kindprozess sauber via `closeGracefully()` (`@PreDestroy`).

  Dies sind die **einzigen** Tool-Beans für die Oracle-DB, die bei `ToolExecutor` registriert
  werden - die frühere Direkt-JDBC-Implementierung wurde entfernt, es gibt kein Umschalt-Flag mehr.

### Datenübertragung / CSV-Export

Damit der bestehende CSV-Export weiter funktioniert, liefert der MCP-Server im
`CallToolResult` zwei Content-Blöcke:
1. Menschenlesbarer Text (identisch zu `QueryResult.toText()`), für die LLM-Antwort/den Chat.
2. Strukturiertes JSON (`{"columns": [...], "rows": [...]}`), das der Client zu einem
   `QueryResult` zusammenbaut – wie bisher.

### Achtung: gepackte Fat-JAR

`aigeny-1.0.0.jar` (Spring-Boot-Repackage) enthält seine Abhängigkeiten unter `BOOT-INF/lib/`,
sichtbar nur über Spring Boots eigenen Classloader. Ein simples `java -cp <fat-jar> MeineKlasse`
würde dort `NoClassDefFoundError` werfen. `OracleMcpConnection#buildJavaArgs()` erkennt diesen
Fall (Classpath = genau ein `.jar`, Classloader beginnt mit `org.springframework.boot.loader`)
und startet den Kindprozess stattdessen über Spring Boots `PropertiesLauncher` mit
`-Dloader.main=com.tschanz.aigeny.database.mcp_server.OracleMcpServerLauncher`, damit der volle
BOOT-INF-Classpath korrekt aufgebaut wird. Im IDE-/`mvn spring-boot:run`-Betrieb (normaler,
mehrteiliger Classpath) wird weiterhin der einfache `-cp <classpath> <MainClass>`-Aufruf genutzt.

## Nächste Schritte / Ausblick

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


