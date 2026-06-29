package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import jakarta.servlet.http.HttpSession;

import java.util.List;

/**
 * Narrow session interface for chat history management (ISP, I-1).
 *
 * <p>Consumers that only need to read or clear the conversation history
 * depend on this interface instead of the broad {@link ChatSessionService}.
 */
public interface SessionHistoryService {

    /**
     * Returns the chat history for the session, creating a new empty list if none exists.
     *
     * @param session HTTP session
     * @return mutable list of messages
     */
    List<Message> getOrCreateHistory(HttpSession session);

    /**
     * Removes all messages from the chat history for the session.
     *
     * @param session HTTP session
     */
    void clearHistory(HttpSession session);
}

