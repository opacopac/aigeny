package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm_tool.QueryResult;
import jakarta.servlet.http.HttpSession;

/**
 * Narrow session interface for query-result export management (ISP, I-1).
 *
 * <p>Consumers that only need to read, write, or check export data
 * depend on this interface instead of the broad {@link ChatSessionService}.
 */
public interface SessionExportService {

    /**
     * Stores a query result in the session so it can be downloaded later.
     *
     * @param session HTTP session
     * @param result  query result to store
     */
    void setLastQueryResult(HttpSession session, QueryResult result);

    /**
     * Retrieves the last query result from the session.
     *
     * @param session HTTP session
     * @return last query result, or {@code null} if none exists
     */
    QueryResult getLastQueryResult(HttpSession session);

    /**
     * Returns {@code true} if a non-empty query result is available in the session.
     *
     * @param session HTTP session
     */
    boolean hasQueryResult(HttpSession session);

    /**
     * Removes the last query result from the session.
     *
     * @param session HTTP session
     */
    void clearLastQueryResult(HttpSession session);
}

