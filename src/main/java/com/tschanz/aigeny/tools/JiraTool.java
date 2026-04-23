package com.tschanz.aigeny.tools;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tschanz.aigeny.web.ChatController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Tool for searching Jira issues via REST API (Jira Server/Data Center).
 */
@Service
public class JiraTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(JiraTool.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RESULTS = 50;

    private final AigenyProperties props;
    private final HttpClient http;

    public JiraTool(AigenyProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override public String getName() { return "search_jira"; }

    @Override
    public String getDescription() {
        return "Search Jira issues using JQL, or fetch a single issue directly by key. " +
               "To fetch a specific ticket, provide 'issueKey' (e.g. 'NOVA-100000') instead of jql. " +
               "JQL example: 'project = NOVA AND status = Open ORDER BY created DESC'";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "jql", Map.of("type", "string", "description", "JQL query string (use this for searches)"),
            "issueKey", Map.of("type", "string", "description", "Fetch a single issue directly by key, e.g. NOVA-100000 (faster and more detailed than JQL)"),
            "maxResults", Map.of("type", "integer", "description", "Max issues to return for JQL searches (default 20, max 50)")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl() == null ? "" : jira.getBaseUrl().replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult("Jira is not configured. Please set aigeny.jira.base-url.");
        }

        // Resolve effective token: ThreadLocal (set by ChatController) takes priority over server config
        String effectiveToken = JiraTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = props.getJira().getToken();
        }

        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult("Kein Jira-Token konfiguriert. Bitte gib deinen persönlichen Jira-Token über den 'Token eingeben'-Button ein.");
        }

        JsonNode args = JSON.readTree(argumentsJson);
        String issueKey = args.path("issueKey").asText("").trim();
        String jql = args.path("jql").asText("").trim();
        int maxResults = Math.min(args.path("maxResults").asInt(20), MAX_RESULTS);


        // Build auth header – always Bearer (Personal Access Token)
        String authHeader = "Bearer " + effectiveToken;
        log.debug("   Auth mode=Bearer tokenLength={}", effectiveToken.length());

        // Direct issue fetch by key - richer data, no JQL needed
        if (!issueKey.isBlank()) {
            return fetchIssueByKey(issueKey, baseUrl, authHeader);
        }

        if (jql.isBlank()) {
            return new ToolResult("Please provide either 'issueKey' or 'jql'.");
        }

        return searchByJql(jql, maxResults, baseUrl, authHeader);
    }

    private ToolResult fetchIssueByKey(String issueKey, String baseUrl, String authHeader) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + URLEncoder.encode(issueKey, StandardCharsets.UTF_8)
                + "?fields=summary,status,assignee,priority,issuetype,created,updated,description,comment";
        log.info(">> JIRA REQUEST  issueKey={}", issueKey);
        log.info("   URL: {}", url);

        HttpResponse<String> response = sendRequest(url, authHeader);

        if (response.statusCode() == 404) {
            return new ToolResult("Jira issue '" + issueKey + "' not found (HTTP 404). " +
                    "Check the key and ensure the PAT has access to this project.");
        }
        if (response.statusCode() == 401) {
            log.warn("   Response body: {}", response.body());
            return new ToolResult("Jira authentication failed (HTTP 401). Check token in ~/.aigeny/aigeny.yml.");
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA RESPONSE status={} body={}", response.statusCode(), response.body());
            return new ToolResult("Jira error " + response.statusCode() + ": " + response.body());
        }

        log.info("<< JIRA RESPONSE status=200 bodySize={}B", response.body().length());
        return parseSingleIssue(response.body());
    }

    private ToolResult searchByJql(String jql, int maxResults, String baseUrl, String authHeader) throws Exception {
        String fields = "summary,status,assignee,priority,issuetype,created,updated";
        String url = baseUrl + "/rest/api/2/search?jql="
                + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&fields=" + fields + "&maxResults=" + maxResults;
        log.info(">> JIRA REQUEST  jql=\"{}\" maxResults={}", jql, maxResults);
        log.info("   URL: {}", url);

        HttpResponse<String> response = sendRequest(url, authHeader);

        if (response.statusCode() == 401) {
            log.warn("   Response body: {}", response.body());
            return new ToolResult("Jira authentication failed (HTTP 401). Check token in ~/.aigeny/aigeny.yml.");
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA RESPONSE status={} body={}", response.statusCode(), response.body());
            return new ToolResult("Jira error " + response.statusCode() + ": " + response.body());
        }

        log.info("<< JIRA RESPONSE status=200 bodySize={}B", response.body().length());
        return parseJiraResponse(response.body());
    }

    private HttpResponse<String> sendRequest(String url, String authHeader) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private ToolResult parseSingleIssue(String json) throws Exception {
        JsonNode f = JSON.readTree(json);
        String key = f.path("key").asText();
        JsonNode fields = f.path("fields");

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(key).append("**: ").append(fields.path("summary").asText()).append("\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Status | ").append(fields.path("status").path("name").asText("-")).append(" |\n");
        sb.append("| Type | ").append(fields.path("issuetype").path("name").asText("-")).append(" |\n");
        sb.append("| Priority | ").append(fields.path("priority").path("name").asText("-")).append(" |\n");
        sb.append("| Assignee | ").append(fields.path("assignee").path("displayName").asText("Unassigned")).append(" |\n");
        sb.append("| Created | ").append(fields.path("created").asText("-")).append(" |\n");
        sb.append("| Updated | ").append(fields.path("updated").asText("-")).append(" |\n");

        String description = fields.path("description").asText("").trim();
        if (!description.isBlank()) {
            sb.append("\n**Description:**\n").append(description).append("\n");
        }

        JsonNode comments = fields.path("comment").path("comments");
        if (comments.isArray() && !comments.isEmpty()) {
            sb.append("\n**Last ").append(Math.min(comments.size(), 3)).append(" comment(s):**\n");
            int start = Math.max(0, comments.size() - 3);
            for (int i = start; i < comments.size(); i++) {
                JsonNode c = comments.get(i);
                sb.append("- *").append(c.path("author").path("displayName").asText("?")).append("*: ")
                  .append(c.path("body").asText("")).append("\n");
            }
        }

        return new ToolResult(sb.toString());
    }

    private ToolResult parseJiraResponse(String json) throws Exception {
        JsonNode root = JSON.readTree(json);
        int total = root.path("total").asInt(0);
        JsonNode issues = root.path("issues");

        if (!issues.isArray() || issues.isEmpty())
            return new ToolResult("No Jira issues found for ze query, da.");

        log.info("   Jira total={} returned={}", total, issues.size());

        List<String> columns = List.of("KEY", "SUMMARY", "STATUS", "ASSIGNEE", "PRIORITY", "TYPE");
        List<Map<String, Object>> rows = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        text.append("Found ").append(total).append(" issues (showing ").append(issues.size()).append("):\n\n");

        for (JsonNode issue : issues) {
            String key = issue.path("key").asText();
            JsonNode f = issue.path("fields");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("KEY",      key);
            row.put("SUMMARY",  f.path("summary").asText(""));
            row.put("STATUS",   f.path("status").path("name").asText(""));
            row.put("ASSIGNEE", f.path("assignee").path("displayName").asText("Unassigned"));
            row.put("PRIORITY", f.path("priority").path("name").asText(""));
            row.put("TYPE",     f.path("issuetype").path("name").asText(""));
            rows.add(row);
            text.append("- ").append(key).append(": ").append(row.get("SUMMARY"))
                .append(" [").append(row.get("STATUS")).append("] (").append(row.get("ASSIGNEE")).append(")\n");
        }

        return new ToolResult(text.toString(), new QueryResult("Jira", columns, rows));
    }
}

