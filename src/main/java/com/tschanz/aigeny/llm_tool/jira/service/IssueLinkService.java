package com.tschanz.aigeny.llm_tool.jira.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm_tool.jira.http.JiraHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Service for creating issue links in Jira.
 */
@Service
public class IssueLinkService {

    private static final Logger log = LoggerFactory.getLogger(IssueLinkService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JiraHttpClient httpClient;

    public IssueLinkService(JiraHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Creates a "Cloners" issue link: sourceKey "is cloned by" newKey / newKey "clones" sourceKey.
     * Failure is non-fatal – logged as a warning only.
     *
     * @param sourceKey The original issue key
     * @param newKey The cloned issue key
     * @param baseUrl Jira base URL
     * @param token Bearer token
     */
    public void createCloneLink(String sourceKey, String newKey, String baseUrl, String token) {
        try {
            // "Cloners" link type: inward = "is cloned by", outward = "clones"
            String body = JSON.writeValueAsString(Map.of(
                    "type", Map.of("name", "Cloners"),
                    "inwardIssue", Map.of("key", newKey),
                    "outwardIssue", Map.of("key", sourceKey)));

            String url = baseUrl + "/rest/api/2/issueLink";
            String authHeader = "Bearer " + token;

            HttpResponse<String> response = httpClient.post(url, body, authHeader);

            if (response.statusCode() == 201) {
                log.info("   Created Cloners link: {} clones {}", newKey, sourceKey);
            } else {
                log.warn("   Could not create Cloners link ({} → {}): status={} body={}",
                        newKey, sourceKey, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("   Exception creating Cloners link: {}", e.getMessage());
        }
    }
}

