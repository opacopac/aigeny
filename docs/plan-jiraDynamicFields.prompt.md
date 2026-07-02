# Plan: Generische Jira-Feld-Verwaltung (Lesen & Schreiben)

**Status:** Entwurf / zur Review
**Datum:** 2026-07-01

## Ziel

Aktuell sind Felder wie `duedate` hart in `QueryJiraTool` und `UpdateJiraIssueTool`
verdrahtet. Jeder neue Feldwunsch erfordert eine Code-Änderung an mehreren Stellen.

Dieser Plan beschreibt, wie die LLM selbst entscheiden kann, welche Jira-Felder
gelesen bzw. geschrieben werden – dynamisch, ohne Code-Änderung pro Feld.

## Design-Entscheidungen

- **Kein Caching** der Feldmetadaten. Metadaten (`/rest/api/2/field`) werden bei
  Bedarf live abgefragt. Das hält das Verhalten vorhersagbar und den Code einfach;
  der zusätzliche HTTP-Call fällt nur in seltenen Fällen an (Feld-Discovery oder
  Auflösung unbekannter Custom-Field-Namen).
- **Feste Standardfelder bleiben bestehen.** Die aktuelle Default-Liste beim Lesen
  eines einzelnen Issues (`summary, status, assignee, priority, issuetype, created,
  updated, duedate, description, comment, attachment, issuelinks, subtasks`) wird
  weiterhin immer mit Werten angezeigt.
- **Zusätzlich gesetzte Felder werden nur als Namensliste angezeigt** (ohne Werte),
  um Tokens zu sparen. Möchte die LLM einen konkreten Wert, fragt sie ihn gezielt
  über den `fields`-Parameter nach. Keine Obergrenze für die Namensliste.
- **JQL-Suche bleibt unverändert kompakt** (keine Zusatzfelder-Erkennung), da dort
  Performance bei vielen Treffern im Vordergrund steht.
- **Standard- vs. Custom-Fields:** Standardfelder haben lesbare, feste IDs (z. B.
  `duedate`) und benötigen keine Namensauflösung. Custom-Fields haben
  instanzspezifische IDs (`customfield_10004`) und werden nur für diese über den
  neuen `JiraFieldService` aufgelöst.

## Schritte

1. **`JiraFieldService`** (neu, in `jira/`)
   Methode `fetchAllFields()` ruft live `GET /rest/api/2/field` ab (ID, Name,
   `custom`-Flag, `schema`) und bietet Name→ID-Auflösung
   (case-insensitive, Teilstring-Suche) – primär für Custom-Fields relevant.

2. **`ListJiraFieldsTool`** (neues Tool, `list_jira_fields`)
   Nutzt `JiraFieldService`, optionaler `search`-Parameter, damit die LLM alle in
   der Jira-Instanz definierten Felder (auch nicht gesetzte) nachschlagen kann.

3. **`QueryJiraTool.fetchIssueByKey()`**
   Request auf `fields=*all` umstellen, um zu erkennen, welche Felder auf dem
   Issue gesetzt sind. Bestehenden optionalen `fields`-Array-Parameter behalten,
   damit die LLM gezielt Werte einzelner (Custom-)Felder nachfragen kann.

4. **`JiraIssueFormatter.formatSingleIssue()`**
   Feste Tabelle (Status, Type, Priority, …) bleibt wie gewohnt mit Werten.
   Zusätzlich wird eine vollständige (ohne Limit) Liste
   „**Weitere gesetzte Felder:** Name1, Name2, …" angehängt (nur Namen via
   `JiraFieldService`, keine Werte) – leere/`null`-Felder werden herausgefiltert.

5. **`UpdateJiraIssueTool`**
   Generischer `fields`-Objekt-Parameter (`{"Story Points": 5}`), der bei Bedarf
   über `JiraFieldService` in Jira-Feld-IDs übersetzt und in die bestehenden
   `params`/`PendingJiraAction` gemergt wird. `summary`/`description`/`duedate`
   bleiben als bequeme Kurz-Parameter erhalten (Operation-Layer unverändert, da
   bereits generisch `fields`-Map durchreicht).

6. **Tool-Beschreibungen anpassen**
   `search_jira` und `update_jira_issue`: LLM muss verstehen, dass die
   Namensliste zusätzlicher Felder nur Hinweise sind – Werte müssen gezielt via
   `fields`-Parameter nachgefragt werden.

7. **Tests**
   Neue/angepasste Tests für `JiraFieldService`, `ListJiraFieldsTool`,
   `QueryJiraTool`, `JiraIssueFormatter`, `UpdateJiraIssueTool`;
   `messages.properties` um neue Tool-/Argument-Beschreibungen erweitern.

## Offene Punkte

1. **Namenskonflikte bei Custom-Fields** im `fields`-Update-Parameter (mehrere
   ähnliche Namen): Fehler mit Vorschlagsliste zurückgeben, oder erstes Match
   nehmen? *(noch zu entscheiden)*

## Betroffene Dateien (Übersicht)

- `src/main/java/com/tschanz/aigeny/jira/JiraFieldService.java` (neu)
- `src/main/java/com/tschanz/aigeny/jira/tool/ListJiraFieldsTool.java` (neu)
- `src/main/java/com/tschanz/aigeny/jira/tool/QueryJiraTool.java`
- `src/main/java/com/tschanz/aigeny/jira/JiraIssueFormatter.java`
- `src/main/java/com/tschanz/aigeny/jira/tool/UpdateJiraIssueTool.java`
- `src/main/resources/messages.properties`
- `src/test/java/com/tschanz/aigeny/jira/**` (neue/angepasste Tests)

