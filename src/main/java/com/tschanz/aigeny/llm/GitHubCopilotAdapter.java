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
 * LLM adapter that uses the GitHub-Copilot-hosted chat completions endpoint.
 * The user must first pair AIgeny with GitHub via the device-flow Connect dialog
 * (see {@link GitHubCopilotService}).
 *
 * Request/response format is OpenAI-compatible; the model name is taken from
 * {@code aigeny.llm.model} (e.g. {@code gpt-4o}, {@code claude-3.5-sonnet}, ...).
 */
public class GitHubCopilotAdapter implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubCopilotAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AigenyProperties props;
    private final GitHubCopilotService github;
    private final HttpClient http;

    public GitHubCopilotAdapter(AigenyProperties props, GitHubCopilotService github) {
        this.props = props;
        this.github = github;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<ToolDefinition> tools) throws Exception {
        AigenyProperties.Llm llm = props.getLlm();

        ObjectNode body = JSON.createObjectNode();
        body.put("model", llm.getModel());

        ArrayNode msgArray = body.putArray("messages");
        for (Message m : messages) {
            ObjectNode mn = msgArray.addObject();
            mn.put("role", m.getRole());
            if (m.getContent() != null) mn.put("content", m.getContent());
            if (m.getToolCallId() != null) mn.put("tool_call_id", m.getToolCallId());
            if (m.getName() != null) mn.put("name", m.getName());
            if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                ArrayNode tcArr = mn.putArray("tool_calls");
                for (ToolCall tc : m.getToolCalls()) {
                    ObjectNode tcn = tcArr.addObject();
                    tcn.put("id", tc.getId());
                    tcn.put("type", "function");
                    ObjectNode fn = tcn.putObject("function");
                    fn.put("name", tc.getFunction().getName());
                    fn.put("arguments", tc.getFunction().getArguments());
                }
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArr = body.putArray("tools");
            for (ToolDefinition td : tools) toolsArr.add(JSON.valueToTree(td));
            body.put("tool_choice", "auto");
        }
        body.put("max_tokens", 8192);

        String bodyStr = JSON.writeValueAsString(body);
        String url = github.getCopilotApiBase().replaceAll("/$", "") + "/chat/completions";

        long userMsgCount = messages.stream().filter(m -> !"system".equals(m.getRole())).count();
        log.info(">> LLM REQUEST  provider=github-copilot model={} messages={} tools={}",
                llm.getModel(), userMsgCount, tools != null ? tools.size() : 0);
        log.info("   URL: {}", url);
        log.debug("   Body: {}", bodyStr);

        String session = github.getCopilotSessionToken();
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(300))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + session)
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8));
        github.copilotHeaders().forEach(reqBuilder::header);

        long t0 = System.currentTimeMillis();
        HttpResponse<String> response = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - t0;

        if (response.statusCode() != 200) {
            log.error("<< LLM RESPONSE status={} elapsed={}ms body={}",
                    response.statusCode(), elapsed, response.body());
            throw new RuntimeException("GitHub Copilot LLM returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }

        ChatResponse chatResponse = parseResponse(response.body());
        if (chatResponse.hasToolCalls()) {
            log.info("<< LLM RESPONSE status=200 elapsed={}ms type=tool_calls count={}",
                    elapsed, chatResponse.getToolCalls().size());
        } else {
            log.info("<< LLM RESPONSE status=200 elapsed={}ms type=text chars={}",
                    elapsed, chatResponse.getContent() != null ? chatResponse.getContent().length() : 0);
        }
        log.debug("   Raw response: {}", response.body());
        return chatResponse;
    }

    private ChatResponse parseResponse(String json) throws Exception {
        JsonNode root = JSON.readTree(json);
        JsonNode choice = root.path("choices").get(0);
        JsonNode message = choice.path("message");
        JsonNode toolCallsNode = message.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            for (JsonNode tc : toolCallsNode) {
                ToolCall call = new ToolCall();
                call.setId(tc.path("id").asText());
                call.setType(tc.path("type").asText("function"));
                ToolCall.FunctionCall fc = new ToolCall.FunctionCall();
                fc.setName(tc.path("function").path("name").asText());
                fc.setArguments(tc.path("function").path("arguments").asText());
                call.setFunction(fc);
                calls.add(call);
            }
            String text = message.path("content").asText("");
            return new ChatResponse(calls, text.isBlank() ? null : text);
        }
        String content = message.path("content").asText("");
        return new ChatResponse(content);
    }
}

