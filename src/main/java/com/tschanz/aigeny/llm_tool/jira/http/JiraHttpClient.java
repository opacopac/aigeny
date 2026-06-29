package com.tschanz.aigeny.llm_tool.jira.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for Jira API communication.
 * Encapsulates common HTTP request/response handling.
 *
 * <p>The default no-arg constructor creates a production-ready {@link HttpClient}.
 * A package-private constructor accepting an {@link HttpClient} is provided for
 * unit testing without a real network connection.
 */
@Component
public class JiraHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JiraHttpClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public JiraHttpClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /** Package-private constructor for unit testing – allows injection of a mock {@link HttpClient}. */
    JiraHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Send a GET request to Jira API.
     */
    public HttpResponse<String> get(String url, String authHeader) throws Exception {
        log.info(">> JIRA GET {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA GET status={} url={}", response.statusCode(), url);

        return response;
    }

    /**
     * Send a PUT request to Jira API.
     */
    public HttpResponse<String> put(String url, String body, String authHeader) throws Exception {
        log.info(">> JIRA PUT {} body={}", url, body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA PUT status={}", response.statusCode());

        return response;
    }

    /**
     * Send a POST request to Jira API.
     */
    public HttpResponse<String> post(String url, String body, String authHeader) throws Exception {
        log.info(">> JIRA POST {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("<< JIRA POST status={}", response.statusCode());

        return response;
    }
}

