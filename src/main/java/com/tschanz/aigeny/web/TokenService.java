package com.tschanz.aigeny.web;

import com.tschanz.aigeny.config.BitbucketConfiguration;
import com.tschanz.aigeny.config.JiraConfiguration;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages user-specific tokens for external services (Jira, Bitbucket).
 * Resolves effective tokens from session overrides or configuration fallbacks.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    // Session attribute keys
    private static final String SESSION_JIRA_TOKEN = "jiraToken";
    private static final String SESSION_BITBUCKET_TOKEN = "bitbucketToken";

    private final JiraConfiguration jiraConfig;
    private final BitbucketConfiguration bitbucketConfig;

    public TokenService(JiraConfiguration jiraConfig, BitbucketConfiguration bitbucketConfig) {
        this.jiraConfig = jiraConfig;
        this.bitbucketConfig = bitbucketConfig;
    }

    /**
     * Returns the effective Jira token for the current session.
     * User-provided session token takes priority over server configuration.
     *
     * @param session HTTP session (may be null)
     * @return effective token or null if not configured
     */
    public String getEffectiveJiraToken(HttpSession session) {
        String userToken = getUserJiraToken(session);
        if (userToken != null && !userToken.isBlank()) {
            return userToken;
        }
        return jiraConfig.getToken();
    }

    /**
     * Returns the effective Bitbucket token for the current session.
     * User-provided session token takes priority over server configuration.
     *
     * @param session HTTP session (may be null)
     * @return effective token or null if not configured
     */
    public String getEffectiveBitbucketToken(HttpSession session) {
        String userToken = getUserBitbucketToken(session);
        if (userToken != null && !userToken.isBlank()) {
            return userToken;
        }
        return bitbucketConfig.getToken();
    }

    /**
     * Stores a user-specific Jira token in the session.
     *
     * @param session HTTP session
     * @param token token to store (blank/null to remove)
     */
    public void setUserJiraToken(HttpSession session, String token) {
        if (token == null || token.isBlank()) {
            session.removeAttribute(SESSION_JIRA_TOKEN);
            log.info("User Jira token removed for session {}", session.getId());
        } else {
            session.setAttribute(SESSION_JIRA_TOKEN, token);
            log.info("User Jira token set for session {}", session.getId());
        }
    }

    /**
     * Stores a user-specific Bitbucket token in the session.
     *
     * @param session HTTP session
     * @param token token to store (blank/null to remove)
     */
    public void setUserBitbucketToken(HttpSession session, String token) {
        if (token == null || token.isBlank()) {
            session.removeAttribute(SESSION_BITBUCKET_TOKEN);
            log.info("User Bitbucket token removed for session {}", session.getId());
        } else {
            session.setAttribute(SESSION_BITBUCKET_TOKEN, token);
            log.info("User Bitbucket token set for session {}", session.getId());
        }
    }

    /**
     * Removes the user-specific Jira token from the session.
     *
     * @param session HTTP session
     */
    public void clearUserJiraToken(HttpSession session) {
        session.removeAttribute(SESSION_JIRA_TOKEN);
        log.info("User Jira token cleared for session {}", session.getId());
    }

    /**
     * Removes the user-specific Bitbucket token from the session.
     *
     * @param session HTTP session
     */
    public void clearUserBitbucketToken(HttpSession session) {
        session.removeAttribute(SESSION_BITBUCKET_TOKEN);
        log.info("User Bitbucket token cleared for session {}", session.getId());
    }

    /**
     * Checks if a Jira token is available (either from user session or server config).
     *
     * @param session HTTP session (may be null)
     * @return true if a token is available
     */
    public boolean hasJiraToken(HttpSession session) {
        String effectiveToken = getEffectiveJiraToken(session);
        return effectiveToken != null && !effectiveToken.isBlank();
    }

    /**
     * Checks if a Bitbucket token is available (either from user session or server config).
     *
     * @param session HTTP session (may be null)
     * @return true if a token is available
     */
    public boolean hasBitbucketToken(HttpSession session) {
        String effectiveToken = getEffectiveBitbucketToken(session);
        return effectiveToken != null && !effectiveToken.isBlank();
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private String getUserJiraToken(HttpSession session) {
        if (session == null) return null;
        return (String) session.getAttribute(SESSION_JIRA_TOKEN);
    }

    private String getUserBitbucketToken(HttpSession session) {
        if (session == null) return null;
        return (String) session.getAttribute(SESSION_BITBUCKET_TOKEN);
    }
}

