package com.tschanz.aigeny.llm;

import com.tschanz.aigeny.llm.model.ChatResponse;
import com.tschanz.aigeny.llm.model.Message;
import com.tschanz.aigeny.llm.model.ToolDefinition;

import java.util.List;

/**
 * Common interface for all LLM backends (Groq, OpenAI, Azure, Ollama).
 */
public interface LlmClient {

    /**
     * Send a chat request and return the response (potentially with tool calls).
     */
    ChatResponse chat(List<Message> messages, List<ToolDefinition> tools) throws Exception;
}

