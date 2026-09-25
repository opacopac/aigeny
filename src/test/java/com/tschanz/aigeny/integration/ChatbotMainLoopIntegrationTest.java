package com.tschanz.aigeny.integration;

import com.tschanz.aigeny.chat.ChatResult;
import com.tschanz.aigeny.integration.support.ChatbotTestHarness;
import com.tschanz.aigeny.integration.support.MockBitbucketTool;
import com.tschanz.aigeny.integration.support.MockDatabaseTool;
import com.tschanz.aigeny.integration.support.MockJiraTool;
import com.tschanz.aigeny.integration.support.MockLlmClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * First "complicated set-up" integration test for the chatbot's main tool-call loop:
 * <ol>
 *   <li>Mock user input: "can you tell me the table name containing products"</li>
 *   <li>Mock LLM responds with a tool call: {@code list_tables} with {@code prefix="P_"}</li>
 *   <li>Mock database tool responds with two matching tables: {@code P_PRODUKTE_E}, {@code P_PRODUKTE_V}</li>
 *   <li>Mock LLM picks {@code P_PRODUKTE_E} and terminates the loop with a plain-text final answer</li>
 * </ol>
 *
 * <p>Mock Jira and Bitbucket tools are wired into the harness too (unused by this particular
 * scenario) to demonstrate that the same setup supports richer, multi-tool scenarios in future tests.
 */
@DisplayName("Chatbot main loop – database table lookup")
class ChatbotMainLoopIntegrationTest {

    @Test
    @DisplayName("resolves the correct table name via a single list_tables tool call")
    void findsProductTable_viaListTablesToolCall() throws Exception {
        // Mock LLM: first calls list_tables(prefix=P_), then answers with the table name
        MockLlmClient llmClient = new MockLlmClient()
                .thenRespondWithToolCall("call-1", "list_tables", "{\"prefix\":\"P_\"}")
                .thenRespondWithText("P_PRODUKTE_E");

        // Mock DB tool returning two candidate tables
        MockDatabaseTool dbTool = MockDatabaseTool.listTables("P_PRODUKTE_E", "P_PRODUKTE_V");

        // Mock Jira/Bitbucket tools wired in too, unused by this scenario
        MockJiraTool jiraTool = new MockJiraTool("query_jira");
        MockBitbucketTool bitbucketTool = new MockBitbucketTool("search_bitbucket");

        ChatbotTestHarness harness = new ChatbotTestHarness(
                llmClient, List.of(dbTool, jiraTool, bitbucketTool));

        // Mock user input drives the real orchestration/tool-call loop
        ChatResult result = harness.sendUserMessage("can you tell me the table name containing products");

        assertThat(result.response()).isEqualTo("P_PRODUKTE_E");

        assertThat(llmClient.getCallCount()).isEqualTo(2);
        assertThat(dbTool.getCallCount()).isEqualTo(1);
        assertThat(dbTool.getLastArguments()).contains("\"prefix\":\"P_\"");

        // unrelated mock tools were never touched
        assertThat(jiraTool.getCallCount()).isZero();
        assertThat(bitbucketTool.getCallCount()).isZero();

        // second LLM call must see the tool's result (both candidate tables) in the conversation
        MockLlmClient.Invocation secondCall = llmClient.getInvocation(1);
        assertThat(secondCall.messages())
                .anyMatch(m -> "tool".equals(m.getRole())
                        && m.getContent() != null
                        && m.getContent().contains("P_PRODUKTE_E")
                        && m.getContent().contains("P_PRODUKTE_V"));
    }
}

