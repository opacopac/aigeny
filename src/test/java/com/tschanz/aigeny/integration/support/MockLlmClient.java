package com.tschanz.aigeny.integration.support;

import com.tschanz.aigeny.llm.LlmClient;
import com.tschanz.aigeny.llm.model.ChatResponse;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm.model.ToolCall;
import com.tschanz.aigeny.llm.model.ToolDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scriptable {@link LlmClient} test double for integration tests.
 *
 * <p>Responses are queued (in the order the mock user/LLM conversation should progress) via
 * {@link #thenRespondWithToolCall} / {@link #thenRespondWithToolCalls} / {@link #thenRespondWithText}
 * and are returned one-by-one for each call {@link com.tschanz.aigeny.orchestration.OrchestrationService}
 * makes to {@link #chat}. Every invocation (exact messages/tools passed by the orchestration loop) is
 * recorded so tests can assert on what the "mock LLM" actually saw.
 */
public class MockLlmClient implements LlmClient {

    /** One recorded call to {@link #chat}. */
    public record Invocation(List<Message> messages, List<ToolDefinition> tools) {}

    private final Deque<ChatResponse> scriptedResponses = new ArrayDeque<>();
    private final List<Invocation> invocations = new ArrayList<>();
    private final AtomicInteger callCount = new AtomicInteger();

    /** Queues a response where the mock LLM calls a single tool. */
    public MockLlmClient thenRespondWithToolCall(String toolCallId, String toolName, String argumentsJson) {
        scriptedResponses.add(new ChatResponse(
                List.of(new ToolCall(toolCallId, new ToolCall.FunctionCall(toolName, argumentsJson)))));
        return this;
    }

    /** Queues a response where the mock LLM calls several tools at once. */
    public MockLlmClient thenRespondWithToolCalls(ToolCall... calls) {
        scriptedResponses.add(new ChatResponse(List.of(calls)));
        return this;
    }

    /** Queues a plain final-text response (no tool calls) - terminates the tool-call loop. */
    public MockLlmClient thenRespondWithText(String text) {
        scriptedResponses.add(new ChatResponse(text));
        return this;
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<ToolDefinition> tools) {
        invocations.add(new Invocation(List.copyOf(messages), tools));
        int callNumber = callCount.incrementAndGet();
        ChatResponse next = scriptedResponses.poll();
        if (next == null) {
            throw new IllegalStateException(
                    "MockLlmClient received an unscripted chat() call (#" + callNumber +
                    "). Add another thenRespondWith...() call to the script for this scenario.");
        }
        return next;
    }

    public int getCallCount()                    { return callCount.get(); }
    public List<Invocation> getInvocations()      { return invocations; }
    public Invocation getInvocation(int index)    { return invocations.get(index); }
    public Invocation getLastInvocation()         { return invocations.get(invocations.size() - 1); }
}

