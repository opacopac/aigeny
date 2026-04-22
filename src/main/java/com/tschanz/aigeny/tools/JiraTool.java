package com.tschanz.aigeny.tools;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.ToolDefinition;
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
        return "Search Jira issues using JQL. Returns key, summary, status, assignee, priority. " +
               "Example: 'project = NOVA AND status = Open ORDER BY created DESC'";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> propsMap = Map.of(
            "jql", Map.of("type", "string", "description", "JQL query string"),
            "maxResults", Map.of("type", "integer", "description", "Max issues to return (default 20, max 50)")
        );
        return new ToolDefinition(getName(), getDescription(),
                Map.of("type", "object", "properties", propsMap, "required", List.of("jql")));
    }

    @Override
    public ToolResult execute(String argumentsJson) throws Exception {
        if (!props.isJiraConfigured()) {
            return new ToolResult("Jira is not configured. Please set aigeny.jira.* properties.");
        }

        JsonNode args = JSON.readTree(argumentsJson);
        String jql = args.path("jql").asText("").trim();
        int maxResults = Math.min(args.path("maxResults").asInt(20), MAX_RESULTS);

        AigenyProperties.Jira jira = props.getJira();
        String baseUrl = jira.getBaseUrl().replaceAll("/$", "");
        String fields = "summary,status,assignee,priority,issuetype,created,updated";
        String url = baseUrl + "/rest/api/2/search?jql="
                + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&fields=" + fields + "&maxResults=" + maxResults;

        log.info(">> JIRA REQUEST  jql=\"{}\" maxResults={}", jql, maxResults);
        log.info("   URL: {}", url);

        String auth = jira.getUsername() + ":" + jira.getToken();
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET().build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - t0;

        if (response.statusCode() == 401) {
            log.warn("<< JIRA RESPONSE status=401 elapsed={}ms — authentication failed", elapsed);
            return new ToolResult("Jira authentication failed. Check username and token in application.yml.");
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA RESPONSE status={} elapsed={}ms body={}", response.statusCode(), elapsed, response.body());
            return new ToolResult("Jira error " + response.statusCode() + ": " + response.body());
        }

        log.info("<< JIRA RESPONSE status=200 elapsed={}ms bodySize={}B", elapsed, response.body().length());

        return parseJiraResponse(response.body());
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

