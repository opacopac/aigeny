package com.tschanz.aigeny.bitbucket;

import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Read-only Bitbucket tool: search/list repositories, branches, commits and files.
 * Supports Bitbucket Server / Data Center REST API v1.
 */
@Service
public class SearchBitbucketTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(SearchBitbucketTool.class);

    private final BitbucketConfiguration bitbucketConfig;
    private final HttpClient http;

    public SearchBitbucketTool(BitbucketConfiguration bitbucketConfig, ObjectMapper objectMapper) {
        super(objectMapper);
        this.bitbucketConfig = bitbucketConfig;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override public String getName() { return "search_bitbucket"; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String action   = args.path("action").asText("?");
            String project  = args.path("projectKey").asText("");
            String repo     = args.path("repoSlug").asText("");
            String branch   = args.path("branch").asText("");
            String path     = args.path("path").asText("");
            String query    = args.path("query").asText("");
            String location = (!project.isBlank() && !repo.isBlank()) ? project + "/" + repo
                            : !project.isBlank() ? project : "";
            return switch (action) {
                case "list_repos"   -> "Repos auflisten" + (location.isBlank() ? "" : " in " + location);
                case "list_branches"-> "Branches auflisten in " + location;
                case "list_files"   -> "Dateien auflisten in " + location + (!path.isBlank() ? "/" + path : "") + (!branch.isBlank() ? " (" + branch + ")" : "");
                case "search_code"  -> "Code-Suche nach \"" + query + "\"" + (location.isBlank() ? "" : " in " + location);
                case "list_commits" -> "Commits auflisten in " + location + (!branch.isBlank() ? " @ " + branch : "");
                default             -> "Bitbucket: " + action;
            };
        } catch (Exception e) {
            return getName();
        }
    }

    @Override
    public String getDescription() {
        return "Search and browse Bitbucket repositories (read-only). " +
               "Actions: 'list_repos' – list repos in a project; " +
               "'list_branches' – list branches of a repo; " +
               "'list_files' – list files/folders in a repo path; " +
               "'search_code' – search for text across repos using Bitbucket query syntax " +
               "(e.g. query='project:MVP_OEVP DiscountCampaignOffersValidator' or " +
               "query='repo:novap_pflege SomeClass' – project/repo filters go IN the query string); " +
               "'list_commits' – recent commits on a branch. " +
               "Always specify 'action'. For repo-level actions also specify 'projectKey' and 'repoSlug'. " +
               "For search_code use the 'query' field with optional project:/repo: prefixes.";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "action",     Map.of("type", "string",
                                 "description", "One of: list_repos, list_branches, list_files, search_code, list_commits"),
            "projectKey", Map.of("type", "string",
                                 "description", "Bitbucket project key (e.g. NOVA)"),
            "repoSlug",   Map.of("type", "string",
                                 "description", "Repository slug/name"),
            "branch",     Map.of("type", "string",
                                 "description", "Branch name (default: default branch)"),
            "path",       Map.of("type", "string",
                                 "description", "Directory or file path inside the repo (for list_files)"),
            "query",      Map.of("type", "string",
                                 "description", "Search term (for search_code)"),
            "limit",      Map.of("type", "integer",
                                 "description", "Max results to return (default 25, max 100)")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap,
                       "required", new String[]{"action"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
                String baseUrl = (bitbucketConfig.getBaseUrl() == null ? "" : bitbucketConfig.getBaseUrl()).replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult("Bitbucket ist nicht konfiguriert (base-url fehlt).");
        }

        String effectiveToken = BitbucketTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = bitbucketConfig.getToken();
        }
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult("Kein Bitbucket-Token gesetzt. Bitte Token im UI eingeben.");
        }

        JsonNode args   = objectMapper.readTree(argumentsJson);
        String action   = args.path("action").asText("").trim();
        String project  = args.path("projectKey").asText("").trim();
        String repo     = args.path("repoSlug").asText("").trim();
        String branch   = args.path("branch").asText("").trim();
        String path     = args.path("path").asText("").trim();
        String query    = args.path("query").asText("").trim();
        int limit       = Math.min(args.path("limit").asInt(25), 100);

        String auth = "Bearer " + effectiveToken;

        return switch (action) {
            case "list_repos"    -> listRepos(baseUrl, auth, project, limit);
            case "list_branches" -> listBranches(baseUrl, auth, project, repo, limit);
            case "list_files"    -> listFiles(baseUrl, auth, project, repo, branch, path, limit);
            case "search_code"   -> searchCode(baseUrl, auth, project, repo, query, limit);
            case "list_commits"  -> listCommits(baseUrl, auth, project, repo, branch, limit);
            default              -> new ToolResult("Unbekannte Aktion: " + action +
                                     ". Erlaubt: list_repos, list_branches, list_files, search_code, list_commits");
        };
    }

    // ── list repos ────────────────────────────────────────────────────────────

    private ToolResult listRepos(String base, String auth, String project, int limit) throws Exception {
        String url = project.isBlank()
                ? base + "/rest/api/1.0/repos?limit=" + limit
                : base + "/rest/api/1.0/projects/" + enc(project) + "/repos?limit=" + limit;
        log.info(">> BB list_repos  url={}", url);
        HttpResponse<String> resp = get(url, auth);
        if (!isOk(resp)) return errorResult(resp);

        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode values = root.path("values");
        if (!values.isArray() || values.isEmpty()) return new ToolResult("Keine Repositories gefunden.");

        StringBuilder sb = new StringBuilder("**Repositories");
        if (!project.isBlank()) sb.append(" in Projekt ").append(project);
        sb.append(":**\n\n");
        sb.append("| Slug | Name | Projekt | Clone URL |\n|---|---|---|---|\n");
        for (JsonNode r : values) {
            String slug    = r.path("slug").asText("-");
            String name    = r.path("name").asText("-");
            String prj     = r.path("project").path("key").asText("-");
            String cloneUrl = "";
            for (JsonNode link : r.path("links").path("clone")) {
                if ("http".equals(link.path("name").asText())) { cloneUrl = link.path("href").asText(""); break; }
            }
            sb.append("| ").append(slug).append(" | ").append(name)
              .append(" | ").append(prj).append(" | ").append(cloneUrl).append(" |\n");
        }
        return new ToolResult(sb.toString());
    }

    // ── list branches ─────────────────────────────────────────────────────────

    private ToolResult listBranches(String base, String auth, String project, String repo, int limit) throws Exception {
        if (project.isBlank() || repo.isBlank()) return new ToolResult("projectKey und repoSlug sind erforderlich.");
        String url = base + "/rest/api/1.0/projects/" + enc(project) + "/repos/" + enc(repo)
                + "/branches?limit=" + limit + "&orderBy=MODIFICATION";
        log.info(">> BB list_branches  url={}", url);
        HttpResponse<String> resp = get(url, auth);
        if (!isOk(resp)) return errorResult(resp);

        JsonNode values = objectMapper.readTree(resp.body()).path("values");
        if (!values.isArray() || values.isEmpty()) return new ToolResult("Keine Branches gefunden.");

        StringBuilder sb = new StringBuilder("**Branches in " + project + "/" + repo + ":**\n\n");
        for (JsonNode b : values) {
            String displayId = b.path("displayId").asText("-");
            boolean isDefault = b.path("isDefault").asBoolean(false);
            sb.append("- `").append(displayId).append("`");
            if (isDefault) sb.append(" _(default)_");
            sb.append("\n");
        }
        return new ToolResult(sb.toString());
    }

    // ── list files ────────────────────────────────────────────────────────────

    private ToolResult listFiles(String base, String auth, String project, String repo,
                                  String branch, String path, int limit) throws Exception {
        if (project.isBlank() || repo.isBlank()) return new ToolResult("projectKey und repoSlug sind erforderlich.");
        StringBuilder url = new StringBuilder(base + "/rest/api/1.0/projects/" + enc(project)
                + "/repos/" + enc(repo) + "/files");
        if (!path.isBlank()) url.append("/").append(path);
        url.append("?limit=").append(limit);
        if (!branch.isBlank()) url.append("&at=refs/heads/").append(enc(branch));
        log.info(">> BB list_files  url={}", url);
        HttpResponse<String> resp = get(url.toString(), auth);
        if (!isOk(resp)) return errorResult(resp);

        JsonNode values = objectMapper.readTree(resp.body()).path("values");
        if (!values.isArray() || values.isEmpty()) return new ToolResult("Keine Dateien gefunden.");

        StringBuilder sb = new StringBuilder("**Dateien in " + project + "/" + repo);
        if (!path.isBlank()) sb.append("/").append(path);
        sb.append(":**\n\n");
        for (JsonNode f : values) sb.append("- `").append(f.asText()).append("`\n");
        return new ToolResult(sb.toString());
    }

    // ── search code ───────────────────────────────────────────────────────────

    private ToolResult searchCode(String base, String auth, String project, String repo,
                                   String query, int limit) throws Exception {
        if (query.isBlank()) return new ToolResult("Suchbegriff (query) ist erforderlich.");

        // Bitbucket Server Code Search API: POST /rest/search/1.0/search with JSON body
        String url = base + "/rest/search/1.0/search";

        // Build JSON body – Bitbucket Server Search accepts project/repo filters
        // directly inside the query string as "project:KEY repo:SLUG <term>".
        StringBuilder effectiveQuery = new StringBuilder();
        if (!project.isBlank()) effectiveQuery.append("project:").append(project).append(" ");
        if (!repo.isBlank())    effectiveQuery.append("repo:").append(repo).append(" ");
        effectiveQuery.append(query.trim());

        String bodyJson = "{\"query\":\""
                + effectiveQuery.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                + "\",\"entities\":{\"code\":{}},\"limits\":{\"primary\":"
                + limit + "}}";
        log.info(">> BB search_code  url={} body={}", url, bodyJson);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 404) {
            return new ToolResult("Bitbucket Code Search ist auf diesem Server nicht verfügbar (Plugin fehlt). " +
                    "Verwende stattdessen list_files und dann read_bitbucket_file um spezifische Dateien zu lesen.");
        }
        if (!isOk(resp)) return errorResult(resp);

        log.debug("<< BB search_code raw response: {}", resp.body());

        JsonNode root = objectMapper.readTree(resp.body());
        // Actual Bitbucket Server Code Search response structure:
        // {"scope": {...}, "code": {"values": [...], "count": N}, "query": {...}}
        // Each value: {"repository": {...}, "file": "<path-string>", "hitContexts": [[{line,text},...]], ...}
        JsonNode codeNode = root.path("code");
        JsonNode hits = codeNode.path("values");
        int count = codeNode.path("count").asInt(0);

        if (!hits.isArray() || hits.isEmpty()) return new ToolResult("Keine Treffer für: " + query);

        // Remove debug fallback – format is now known
        StringBuilder sb = new StringBuilder("**Code-Suche nach \"" + query + "\"");
        if (!project.isBlank()) sb.append(" in ").append(project);
        sb.append(" – ").append(count).append(" Treffer:**\n\n");

        for (JsonNode hit : hits) {
            // "repository" is a top-level field in the hit
            String hitProject = hit.path("repository").path("project").path("key").asText("-");
            String hitRepo    = hit.path("repository").path("slug").asText("-");
            // "file" is a plain string with the full path
            String filePath   = hit.path("file").asText("-");

            sb.append("- **").append(hitProject).append("/").append(hitRepo)
              .append("** `").append(filePath).append("`\n");

            // hitContexts is an array of arrays of {line, text} objects
            JsonNode contextGroups = hit.path("hitContexts");
            if (contextGroups.isArray()) {
                for (JsonNode group : contextGroups) {
                    if (!group.isArray()) continue;
                    for (JsonNode lineNode : group) {
                        String text = lineNode.path("text").asText("").trim();
                        if (text.isBlank()) continue;
                        // Strip HTML: <em> tags (match markers) and HTML entities
                        text = text.replaceAll("</?em>", "**")
                                   .replace("&quot;", "\"")
                                   .replace("&lt;", "<")
                                   .replace("&gt;", ">")
                                   .replace("&amp;", "&")
                                   .replace("&#x2F;", "/")
                                   .replaceAll("&[a-zA-Z0-9#]+;", "");
                        int lineNum = lineNode.path("line").asInt(0);
                        sb.append("  ").append(lineNum > 0 ? lineNum + ": " : "").append(text).append("\n");
                    }
                }
            }
            sb.append("\n");
        }
        return new ToolResult(sb.toString());
    }

    // ── list commits ──────────────────────────────────────────────────────────

    private ToolResult listCommits(String base, String auth, String project, String repo,
                                    String branch, int limit) throws Exception {
        if (project.isBlank() || repo.isBlank()) return new ToolResult("projectKey und repoSlug sind erforderlich.");
        StringBuilder url = new StringBuilder(base + "/rest/api/1.0/projects/" + enc(project)
                + "/repos/" + enc(repo) + "/commits?limit=" + limit);
        if (!branch.isBlank()) url.append("&until=refs/heads/").append(enc(branch));
        log.info(">> BB list_commits  url={}", url);
        HttpResponse<String> resp = get(url.toString(), auth);
        if (!isOk(resp)) return errorResult(resp);

        JsonNode values = objectMapper.readTree(resp.body()).path("values");
        if (!values.isArray() || values.isEmpty()) return new ToolResult("Keine Commits gefunden.");

        StringBuilder sb = new StringBuilder("**Commits in " + project + "/" + repo);
        if (!branch.isBlank()) sb.append(" @ ").append(branch);
        sb.append(":**\n\n");
        sb.append("| Hash | Autor | Datum | Nachricht |\n|---|---|---|---|\n");
        for (JsonNode c : values) {
            String hash    = c.path("displayId").asText(c.path("id").asText("-").substring(0, Math.min(8, c.path("id").asText("").length())));
            String author  = c.path("author").path("displayName").asText(c.path("author").path("name").asText("-"));
            long   ts      = c.path("authorTimestamp").asLong(0);
            String date    = ts > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(ts)) : "-";
            String msg     = c.path("message").asText("-").replace("\n", " ").replace("|", "\\|");
            if (msg.length() > 80) msg = msg.substring(0, 77) + "...";
            sb.append("| `").append(hash).append("` | ").append(author)
              .append(" | ").append(date).append(" | ").append(msg).append(" |\n");
        }
        return new ToolResult(sb.toString());
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    /**
     * Resolves a Bitbucket Server "path" node to a full file path string.
     * The node can be a plain string or an object with various fields:
     *   - "toString": "src/main/java/Foo.java"  (preferred)
     *   - "components": ["src","main","java","Foo.java"]  (fallback)
     *   - "parent" + "name": "src/main/java" + "Foo.java"  (fallback)
     *   - "name": "Foo.java"  (last resort)
     */
    private static String resolvePath(JsonNode pathNode) {
        if (pathNode == null || pathNode.isMissingNode()) return "-";
        if (pathNode.isTextual()) return pathNode.asText("-");
        // "toString" field (Bitbucket serializes the Java toString())
        String ts = pathNode.path("toString").asText("");
        if (!ts.isBlank()) return ts;
        // components array: ["src", "main", "java", "Foo.java"]
        JsonNode components = pathNode.path("components");
        if (components.isArray() && !components.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : components) {
                if (!sb.isEmpty()) sb.append("/");
                sb.append(c.asText());
            }
            return sb.toString();
        }
        // parent + name
        String parent = pathNode.path("parent").asText("");
        String name   = pathNode.path("name").asText("");
        if (!name.isBlank()) return parent.isBlank() ? name : parent + "/" + name;
        return "-";
    }

    private HttpResponse<String> get(String url, String auth) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .header("Accept", "application/json")
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private boolean isOk(HttpResponse<String> resp) { return resp.statusCode() == 200; }

    private ToolResult errorResult(HttpResponse<String> resp) {
        if (resp.statusCode() == 401) return new ToolResult("Bitbucket: Authentifizierung fehlgeschlagen – Token ungültig oder abgelaufen.");
        if (resp.statusCode() == 403) return new ToolResult("Bitbucket: Keine Berechtigung für diese Ressource.");
        if (resp.statusCode() == 404) return new ToolResult("Bitbucket: Ressource nicht gefunden (404).");
        return new ToolResult("Bitbucket HTTP-Fehler " + resp.statusCode() + ": " + resp.body());
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
