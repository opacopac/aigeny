package com.tschanz.aigeny.llm_tool.bitbucket;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
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
 * Read-only tool to fetch the raw content of a single file from a Bitbucket repository.
 */
@Service
public class ReadBitbucketFileTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(ReadBitbucketFileTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_CHARS = 20_000; // guard against huge files

    private final AigenyProperties props;
    private final HttpClient http;

    public ReadBitbucketFileTool(AigenyProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override public String getName() { return "read_bitbucket_file"; }

    @Override
    public String getDescription() {
        return "Read the raw content of a single file from a Bitbucket repository (read-only). " +
               "Provide 'projectKey', 'repoSlug', 'filePath' (e.g. 'src/main/java/Foo.java'). " +
               "Optionally 'branch' (defaults to the repo's default branch).";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "projectKey", Map.of("type", "string", "description", "Bitbucket project key (e.g. NOVA)"),
            "repoSlug",   Map.of("type", "string", "description", "Repository slug/name"),
            "filePath",   Map.of("type", "string", "description", "Path to the file inside the repo, e.g. src/main/java/com/example/Foo.java"),
            "branch",     Map.of("type", "string", "description", "Branch name (optional, defaults to default branch)")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap,
                       "required", new String[]{"projectKey", "repoSlug", "filePath"}));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        AigenyProperties.Bitbucket bb = props.getBitbucket();
        String baseUrl = (bb.getBaseUrl() == null ? "" : bb.getBaseUrl()).replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult("Bitbucket ist nicht konfiguriert (base-url fehlt).");
        }

        String effectiveToken = BitbucketTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) effectiveToken = bb.getToken();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult("Kein Bitbucket-Token gesetzt. Bitte Token im UI eingeben.");
        }

        JsonNode args   = JSON.readTree(argumentsJson);
        String project  = args.path("projectKey").asText("").trim();
        String repo     = args.path("repoSlug").asText("").trim();
        String filePath = args.path("filePath").asText("").trim();
        String branch   = args.path("branch").asText("").trim();

        if (project.isBlank() || repo.isBlank() || filePath.isBlank()) {
            return new ToolResult("projectKey, repoSlug und filePath sind erforderlich.");
        }

        String auth = "Bearer " + effectiveToken;

        // Bitbucket Server raw file API: /rest/api/1.0/projects/{key}/repos/{slug}/raw/{path}
        StringBuilder url = new StringBuilder(baseUrl + "/rest/api/1.0/projects/" + enc(project)
                + "/repos/" + enc(repo) + "/raw/" + filePath);
        if (!branch.isBlank()) url.append("?at=refs/heads/").append(enc(branch));

        log.info(">> BB read_file  project={} repo={} file={}", project, repo, filePath);
        log.info("   URL: {}", url);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", auth)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() == 401) return new ToolResult("Bitbucket: Authentifizierung fehlgeschlagen.");
        if (resp.statusCode() == 403) return new ToolResult("Bitbucket: Keine Berechtigung.");
        if (resp.statusCode() == 404) return new ToolResult("Datei nicht gefunden: " + filePath + " in " + project + "/" + repo);
        if (resp.statusCode() != 200) return new ToolResult("Bitbucket HTTP-Fehler " + resp.statusCode() + ": " + resp.body());

        String content = resp.body();
        String truncNote = "";
        if (content.length() > MAX_CHARS) {
            content = content.substring(0, MAX_CHARS);
            truncNote = "\n\n_[Datei wurde auf " + MAX_CHARS + " Zeichen gekürzt]_";
        }

        log.info("<< BB read_file  status=200 chars={}", content.length());

        // Detect language from extension for markdown code block
        String ext = filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.') + 1) : "";
        String lang = switch (ext.toLowerCase()) {
            case "java" -> "java";
            case "js", "ts" -> "javascript";
            case "py" -> "python";
            case "xml" -> "xml";
            case "yml", "yaml" -> "yaml";
            case "json" -> "json";
            case "sql" -> "sql";
            case "sh" -> "bash";
            case "md" -> "markdown";
            default -> "";
        };

        return new ToolResult(
            "**" + project + "/" + repo + " – `" + filePath + "`**\n\n" +
            "```" + lang + "\n" + content + "\n```" + truncNote
        );
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}

