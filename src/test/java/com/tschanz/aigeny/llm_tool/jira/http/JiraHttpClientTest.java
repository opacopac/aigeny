package com.tschanz.aigeny.llm_tool.jira.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JiraHttpClient}.
 *
 * <p>Uses the package-private constructor that accepts a mock {@link HttpClient},
 * so no real network connections are made.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JiraHttpClient")
class JiraHttpClientTest {

    @Mock private HttpClient mockHttpClient;
    @Mock private HttpResponse<String> mockResponse;

    private JiraHttpClient jiraHttpClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        jiraHttpClient = new JiraHttpClient(mockHttpClient);
        // Default stub – most tests just need a non-throwing call
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
    }

    // ── GET ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        @DisplayName("returns the HTTP response from the underlying client")
        void returnsResponse() throws Exception {
            when(mockResponse.statusCode()).thenReturn(200);

            HttpResponse<String> result = jiraHttpClient.get("https://jira.example.com/rest/api/2/issue/NOVA-1", "Bearer token");

            assertThat(result).isSameAs(mockResponse);
            assertThat(result.statusCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("sends GET method")
        @SuppressWarnings("unchecked")
        void sendsGetMethod() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.get("https://jira.example.com/api", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().method()).isEqualTo("GET");
        }

        @Test
        @DisplayName("sends Authorization header with provided value")
        @SuppressWarnings("unchecked")
        void sendsAuthorizationHeader() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.get("https://jira.example.com/api", "Bearer my-secret-token");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Authorization"))
                    .contains("Bearer my-secret-token");
        }

        @Test
        @DisplayName("sends Accept: application/json header")
        @SuppressWarnings("unchecked")
        void sendsAcceptJsonHeader() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.get("https://jira.example.com/api", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Accept"))
                    .contains("application/json");
        }

        @Test
        @DisplayName("sends request to the exact URL provided")
        @SuppressWarnings("unchecked")
        void sendsToCorrectUrl() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            String url = "https://jira.example.com/rest/api/2/search?jql=project%3DNOVA";

            jiraHttpClient.get(url, "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().uri().toString()).isEqualTo(url);
        }
    }

    // ── PUT ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("put()")
    class Put {

        @Test
        @DisplayName("sends PUT method")
        @SuppressWarnings("unchecked")
        void sendsPutMethod() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.put("https://jira.example.com/rest/api/2/issue/NOVA-1", "{}", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().method()).isEqualTo("PUT");
        }

        @Test
        @DisplayName("sends Content-Type: application/json header")
        @SuppressWarnings("unchecked")
        void sendsContentTypeHeader() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.put("https://jira.example.com/api", "{}", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Content-Type"))
                    .contains("application/json");
        }
    }

    // ── POST ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("post()")
    class Post {

        @Test
        @DisplayName("sends POST method")
        @SuppressWarnings("unchecked")
        void sendsPostMethod() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.post("https://jira.example.com/rest/api/2/issue", "{}", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().method()).isEqualTo("POST");
        }

        @Test
        @DisplayName("sends Content-Type: application/json header")
        @SuppressWarnings("unchecked")
        void sendsContentTypeHeader() throws Exception {
            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

            jiraHttpClient.post("https://jira.example.com/api", "{}", "Bearer t");

            verify(mockHttpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            assertThat(captor.getValue().headers().firstValue("Content-Type"))
                    .contains("application/json");
        }
    }
}


