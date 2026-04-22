package com.tschanz.aigeny.llm;

import com.tschanz.aigeny.config.AigenyProperties;
import com.tschanz.aigeny.llm.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Claude API adapter.
 *
 * Claude uses a different request/response format from the OpenAI API:
 * - System message is a top-level field, not part of messages[]
 * - Tool definitions use "input_schema" instead of "parameters"
 * - Tool call results are sent as user messages with type "tool_result"
 * - Response content is a typed array of blocks (text / tool_use)
 *
 * Supported models: claude-opus-4-5, claude-sonnet-4-5, claude-haiku-3-5, etc.
 * Configure: aigeny.llm.provider=claude, aigeny.llm.base-url=https://api.anthropic.com/v1
 */
public class AnthropicAdapter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AigenyProperties props;
    private final HttpClient http;

    public AnthropicAdapter(AigenyProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<ToolDefinition> tools) throws Exception {
        AigenyProperties.Llm llm = props.getLlm();

        ObjectNode body = JSON.createObjectNode();
        body.put("model", llm.getModel());
        body.put("max_tokens", 4096);

        // Extract system message (Claude takes it as a top-level field)
        String systemContent = null;
        List<Message> chatMessages = new ArrayList<>();
        for (Message m : messages) {
            if ("system".equals(m.getRole())) {
                systemContent = m.getContent();
            } else {
                chatMessages.add(m);
            }
        }
        if (systemContent != null) {
            body.put("system", systemContent);
        }

        // Build messages array — Claude format
        ArrayNode msgArray = body.putArray("messages");
        for (Message m : chatMessages) {
            if ("tool".equals(m.getRole())) {
                // Tool result → user message with tool_result content block
                ObjectNode userMsg = msgArray.addObject();
                userMsg.put("role", "user");
                ArrayNode content = userMsg.putArray("content");
                ObjectNode block = content.addObject();
                block.put("type", "tool_result");
                block.put("tool_use_id", m.getToolCallId());
                block.put("content", m.getContent());
            } else if ("assistant".equals(m.getRole()) && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                // Assistant tool-use message
                ObjectNode assistantMsg = msgArray.addObject();
                assistantMsg.put("role", "assistant");
                ArrayNode content = assistantMsg.putArray("content");
                for (ToolCall tc : m.getToolCalls()) {
                    ObjectNode block = content.addObject();
                    block.put("type", "tool_use");
                    block.put("id", tc.getId());
                    block.put("name", tc.getFunction().getName());
                    // Arguments are a JSON string — parse back to node
                    try {
                        block.set("input", JSON.readTree(tc.getFunction().getArguments()));
                    } catch (Exception e) {
                        block.put("input", tc.getFunction().getArguments());
                    }
                }
            } else {
                ObjectNode mn = msgArray.addObject();
                mn.put("role", m.getRole());
                mn.put("content", m.getContent() != null ? m.getContent() : "");
            }
        }

        // Tools — Claude uses "input_schema" instead of "parameters"
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolDefinition td : tools) {
                ObjectNode tn = toolsArr.addObject();
                tn.put("name", td.getFunction().getName());
                tn.put("description", td.getFunction().getDescription());
                tn.set("input_schema", JSON.valueToTree(td.getFunction().getParameters()));
            }
        }

        String bodyStr = JSON.writeValueAsString(body);
        String url = props.getLlm().getBaseUrl().replaceAll("/$", "") + "/messages";

        long userMsgCount = chatMessages.stream().filter(m -> "user".equals(m.getRole())).count();
        log.info(">> LLM REQUEST  provider=claude model={} messages={} tools={}", llm.getModel(), userMsgCount, tools != null ? tools.size() : 0);
        log.info("   URL: {}", url);
        log.debug("   Body: {}", bodyStr);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("x-api-key", llm.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8))
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - t0;

        if (response.statusCode() != 200) {
            log.error("<< LLM RESPONSE status={} elapsed={}ms body={}", response.statusCode(), elapsed, response.body());
            throw new RuntimeException("Claude returned HTTP " + response.statusCode() + ": " + response.body());
        }

        ChatResponse chatResponse = parseResponse(response.body());
        if (chatResponse.hasToolCalls()) {
            log.info("<< LLM RESPONSE status=200 elapsed={}ms type=tool_calls count={}", elapsed, chatResponse.getToolCalls().size());
            chatResponse.getToolCalls().forEach(tc -> log.info("   tool_call: {}", tc.getFunction().getName()));
        } else {
            log.info("<< LLM RESPONSE status=200 elapsed={}ms type=text chars={}", elapsed, chatResponse.getContent() != null ? chatResponse.getContent().length() : 0);
        }
        log.debug("   Raw response: {}", response.body());
        return chatResponse;
    }

    private ChatResponse parseResponse(String json) throws Exception {
        JsonNode root = JSON.readTree(json);
        String stopReason = root.path("stop_reason").asText("");
        JsonNode content = root.path("content");

        // Collect tool_use blocks
        List<ToolCall> toolCalls = new ArrayList<>();
        StringBuilder textContent = new StringBuilder();

        for (JsonNode block : content) {
            String type = block.path("type").asText();
            if ("tool_use".equals(type)) {
                ToolCall tc = new ToolCall();
                tc.setId(block.path("id").asText());
                tc.setType("function");
                ToolCall.FunctionCall fc = new ToolCall.FunctionCall();
                fc.setName(block.path("name").asText());
                // input is a JSON object — serialize back to string for our common format
                fc.setArguments(JSON.writeValueAsString(block.path("input")));
                tc.setFunction(fc);
                toolCalls.add(tc);
            } else if ("text".equals(type)) {
                textContent.append(block.path("text").asText());
            }
        }

        if (!toolCalls.isEmpty()) {
            return new ChatResponse(toolCalls);
        }
        return new ChatResponse(textContent.toString());
    }
}

