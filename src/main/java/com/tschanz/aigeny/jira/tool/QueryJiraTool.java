package com.tschanz.aigeny.jira.tool;
import com.tschanz.aigeny.jira.JiraTokenContext;
import com.tschanz.aigeny.jira.JiraIssueFormatter;

import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.AbstractTool;
import com.tschanz.aigeny.tool.ToolResult;
import com.tschanz.aigeny.jira.JiraHttpClient;
import com.tschanz.aigeny.Messages;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tool for searching Jira issues via REST API (Jira Server/Data Center).
 */
@Service
public class QueryJiraTool extends AbstractTool {

    private static final Logger log = LoggerFactory.getLogger(QueryJiraTool.class);
    private static final int MAX_RESULTS = 50;

    // ── Message keys ─────────────────────────────────────────────────────────
    private static final String MSG_NOT_CONFIGURED   = "jira.error.not_configured";
    private static final String MSG_NO_TOKEN         = "jira.error.no_token";
    private static final String MSG_MISSING_JQL      = "jira.error.missing_jql_or_key";
    private static final String MSG_ISSUE_NOT_FOUND  = "jira.error.issue_not_found";
    private static final String MSG_AUTH_FAILED      = "jira.error.auth_failed_en";
    private static final String MSG_HTTP_ERROR       = "jira.error.http_en";

    private final JiraConfiguration jiraConfig;
    private final JiraHttpClient jiraHttpClient;
    private final JiraIssueFormatter formatter;

    public QueryJiraTool(JiraConfiguration jiraConfig, ObjectMapper objectMapper,
                         JiraHttpClient jiraHttpClient, JiraIssueFormatter formatter) {
        super(objectMapper);
        this.jiraConfig = jiraConfig;
        this.jiraHttpClient = jiraHttpClient;
        this.formatter = formatter;
    }

    @Override public String getName() { return "search_jira"; }

    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String issueKey = args.path("issueKey").asText("").trim();
            String jql      = args.path("jql").asText("").trim();
            if (!issueKey.isBlank()) return "Jira-Ticket lesen: " + issueKey;
            if (!jql.isBlank()) return "Jira-Suche: " + (jql.length() > 60 ? jql.substring(0, 57) + "..." : jql);
        } catch (Exception ignored) {}
        return "Jira durchsuchen";
    }

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
                String baseUrl = jiraConfig.getBaseUrl() == null ? "" : jiraConfig.getBaseUrl().replaceAll("/$", "");

        if (baseUrl.isBlank()) {
            return new ToolResult(Messages.get(MSG_NOT_CONFIGURED));
        }

        // Resolve effective token: ThreadLocal (set by ChatController) takes priority over server config
        String effectiveToken = JiraTokenContext.get();
        if (effectiveToken == null || effectiveToken.isBlank()) {
            effectiveToken = jiraConfig.getToken();
        }

        if (effectiveToken == null || effectiveToken.isBlank()) {
            return new ToolResult(Messages.get(MSG_NO_TOKEN));
        }

        JsonNode args = objectMapper.readTree(argumentsJson);
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
            return new ToolResult(Messages.get(MSG_MISSING_JQL));
        }

        return searchByJql(jql, maxResults, baseUrl, authHeader);
    }

    private ToolResult fetchIssueByKey(String issueKey, String baseUrl, String authHeader) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + URLEncoder.encode(issueKey, StandardCharsets.UTF_8)
                + "?fields=summary,status,assignee,priority,issuetype,created,updated,description,comment,attachment,issuelinks,subtasks";
        log.info(">> JIRA REQUEST  issueKey={}", issueKey);
        log.info("   URL: {}", url);

        HttpResponse<String> response = sendRequest(url, authHeader);

        if (response.statusCode() == 404) {
            return new ToolResult(Messages.get(MSG_ISSUE_NOT_FOUND, issueKey));
        }
        if (response.statusCode() == 401) {
            log.warn("   Response body: {}", response.body());
            return new ToolResult(Messages.get(MSG_AUTH_FAILED));
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA RESPONSE status={} body={}", response.statusCode(), response.body());
            return new ToolResult(Messages.get(MSG_HTTP_ERROR, response.statusCode(), response.body()));
        }

        log.info("<< JIRA RESPONSE status=200 bodySize={}B", response.body().length());
        return formatter.formatSingleIssue(response.body());
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
            return new ToolResult(Messages.get(MSG_AUTH_FAILED));
        }
        if (response.statusCode() != 200) {
            log.error("<< JIRA RESPONSE status={} body={}", response.statusCode(), response.body());
            return new ToolResult(Messages.get(MSG_HTTP_ERROR, response.statusCode(), response.body()));
        }

        log.info("<< JIRA RESPONSE status=200 bodySize={}B", response.body().length());
        return formatter.formatSearchResponse(response.body());
    }

    private HttpResponse<String> sendRequest(String url, String authHeader) throws Exception {
        return jiraHttpClient.get(url, authHeader);
    }
}
