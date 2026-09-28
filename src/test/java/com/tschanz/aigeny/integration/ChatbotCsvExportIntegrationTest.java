package com.tschanz.aigeny.integration;

import com.tschanz.aigeny.chat.ChatResult;
import com.tschanz.aigeny.integration.support.ChatbotTestHarness;
import com.tschanz.aigeny.integration.support.MockDatabaseTool;
import com.tschanz.aigeny.integration.support.MockLlmClient;
import com.tschanz.aigeny.tool.QueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the "tabular DB result → CSV download hint" scenario:
 * <ol>
 *   <li>Mock user input: a question about data in the database</li>
 *   <li>Mock LLM responds with a tool call: {@code run_query} with some SQL</li>
 *   <li>Mock database tool returns a structured {@link QueryResult} (columns + rows), just like
 *       a real {@code run_query}/{@code GenericOracleMcpTool} call would</li>
 *   <li>Mock LLM terminates the loop with a final answer that presents the rows as a list and
 *       tells the user the result can also be downloaded as CSV via the export button</li>
 * </ol>
 *
 * <p>Besides checking the response text, this also asserts that the {@link ChatResult} carries
 * the structured {@link QueryResult} through {@code lastToolResult()} - this is exactly what
 * {@code ChatStreamingService}/{@code SessionExportService} store in the HTTP session so the
 * real {@code GET /api/export/csv} endpoint can later serve the same data as a CSV file.
 */
@DisplayName("Chatbot main loop – DB query result available for CSV export")
class ChatbotCsvExportIntegrationTest {

    @Test
    @DisplayName("presents query rows as a list and mentions the CSV download button")
    void presentsRowsAsListAndMentionsCsvDownload_viaRunQueryToolCall() throws Exception {
        List<String> columns = List.of("PRODUKT_NAME", "TARIFKLASSE");
        List<Map<String, Object>> rows = List.of(
                Map.of("PRODUKT_NAME", "GA", "TARIFKLASSE", "1"),
                Map.of("PRODUKT_NAME", "Halbtax", "TARIFKLASSE", "2")
        );

        // Mock LLM: first calls run_query, then presents the rows and points to the CSV button
        MockLlmClient llmClient = new MockLlmClient()
                .thenRespondWithToolCall("call-1", "run_query",
                        "{\"sql\":\"SELECT PRODUKT_NAME, TARIFKLASSE FROM P_PRODUKTE_V\"}")
                .thenRespondWithText(
                        "Hier sind die Produkte:\n"
                        + "- GA (Tarifklasse 1)\n"
                        + "- Halbtax (Tarifklasse 2)\n\n"
                        + "Du kannst das vollständige Ergebnis auch über den CSV-Button herunterladen.");

        // Mock DB tool returning a structured QueryResult (columns + rows), enabling CSV export
        MockDatabaseTool dbTool = MockDatabaseTool.runQuery(columns, rows);

        ChatbotTestHarness harness = new ChatbotTestHarness(llmClient, List.of(dbTool));

        // Mock user input drives the real orchestration/tool-call loop
        ChatResult result = harness.sendUserMessage("welche Produkte gibt es in der Datenbank?");

        // Final answer presents the data as a list and mentions the CSV download option
        assertThat(result.response()).contains("GA (Tarifklasse 1)", "Halbtax (Tarifklasse 2)");
        assertThat(result.response()).containsIgnoringCase("CSV");

        // The structured query result is carried through so it can be exported as CSV
        assertThat(result.hasExportData()).isTrue();
        QueryResult exportedResult = result.lastToolResult().getQueryResult();
        assertThat(exportedResult.getColumns()).containsExactlyElementsOf(columns);
        assertThat(exportedResult.getRows()).containsExactlyElementsOf(rows);

        assertThat(llmClient.getCallCount()).isEqualTo(2);
        assertThat(dbTool.getCallCount()).isEqualTo(1);
        assertThat(dbTool.getLastArguments()).contains("SELECT PRODUKT_NAME, TARIFKLASSE");

        // second LLM call must see the tool's textual result in the conversation
        MockLlmClient.Invocation secondCall = llmClient.getInvocation(1);
        assertThat(secondCall.messages())
                .anyMatch(m -> "tool".equals(m.getRole())
                        && m.getContent() != null
                        && m.getContent().contains("GA")
                        && m.getContent().contains("Halbtax"));
    }
}

