package com.tschanz.aigeny.chat;

import com.tschanz.aigeny.llm.model.Message;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages chat conversation history stored in HTTP session.
 * 
 * <p>This service is responsible for:</p>
 * <ul>
 *   <li>Creating and retrieving chat history</li>
 *   <li>Clearing chat history</li>
 * </ul>
 * 
 * <p>Single Responsibility: Chat history lifecycle management</p>
 */
@Service
public class HistoryManager {

    private static final Logger log = LoggerFactory.getLogger(HistoryManager.class);

    private static final String SESSION_HISTORY = "chatHistory";

    /**
     * Retrieves the chat history for the session, creating a new one if it doesn't exist.
     *
     * @param session HTTP session
     * @return mutable list of messages
     */
    @SuppressWarnings("unchecked")
    public List<Message> getOrCreateHistory(HttpSession session) {
        List<Message> history = (List<Message>) session.getAttribute(SESSION_HISTORY);
        if (history == null) {
            history = new ArrayList<>();
            session.setAttribute(SESSION_HISTORY, history);
            log.debug("Created new chat history for session {}", session.getId());
        }
        return history;
    }

    /**
     * Clears the chat history for the session.
     *
     * @param session HTTP session
     */
    public void clearHistory(HttpSession session) {
        session.removeAttribute(SESSION_HISTORY);
        log.info("Chat history cleared for session {}", session.getId());
    }
}
