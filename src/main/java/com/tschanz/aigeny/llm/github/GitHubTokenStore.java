package com.tschanz.aigeny.llm.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Manages persistent storage of GitHub OAuth tokens in the user's home directory.
 * <p>
 * Tokens are stored in ~/.aigeny/github-copilot.json with restricted file permissions
 * on POSIX systems to protect the sensitive OAuth token.
 */
@Service
public class GitHubTokenStore {

    private static final Logger log = LoggerFactory.getLogger(GitHubTokenStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path tokenFile;

    public GitHubTokenStore() {
        this.tokenFile = Path.of(System.getProperty("user.home"), ".aigeny", "github-copilot.json");
    }

    /**
     * Constructs a token store with a custom file path (useful for testing).
     *
     * @param tokenFile the path to the token file
     */
    public GitHubTokenStore(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    /**
     * Persists the OAuth token to disk.
     *
     * @param token the GitHub OAuth token to save
     */
    public void save(String token) {
        try {
            Files.createDirectories(tokenFile.getParent());
            ObjectNode obj = JSON.createObjectNode();
            obj.put("access_token", token);
            obj.put("saved_at", Instant.now().toString());
            Files.writeString(tokenFile, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(obj));

            // Best-effort: restrict to owner only on POSIX systems
            restrictFilePermissions();

            log.debug("GitHub OAuth token persisted to {}", tokenFile);
        } catch (IOException e) {
            log.warn("Could not persist GitHub token to {}: {}", tokenFile, e.getMessage());
        }
    }

    /**
     * Loads the stored OAuth token from disk.
     *
     * @return the stored token, or empty if no token exists or reading fails
     */
    public Optional<String> load() {
        if (!Files.exists(tokenFile)) {
            return Optional.empty();
        }

        try {
            String content = Files.readString(tokenFile);
            JsonNode node = JSON.readTree(content);
            String token = node.path("access_token").asText(null);
            if (token != null && !token.isBlank()) {
                log.debug("GitHub OAuth token loaded from {}", tokenFile);
                return Optional.of(token);
            }
        } catch (IOException e) {
            log.warn("Could not read token from {}: {}", tokenFile, e.getMessage());
        }

        return Optional.empty();
    }

    /**
     * Deletes the stored token file.
     */
    public void delete() {
        try {
            if (Files.deleteIfExists(tokenFile)) {
                log.debug("GitHub OAuth token deleted from {}", tokenFile);
            }
        } catch (IOException e) {
            log.warn("Could not delete token file {}: {}", tokenFile, e.getMessage());
        }
    }

    /**
     * Checks if a token file exists.
     *
     * @return true if the token file exists
     */
    public boolean exists() {
        return Files.exists(tokenFile);
    }

    /**
     * Gets the path to the token file.
     *
     * @return the token file path
     */
    public Path getTokenFilePath() {
        return tokenFile;
    }

    /**
     * Attempts to restrict file permissions to owner-only on POSIX systems.
     * Silently ignores errors (e.g., on Windows).
     */
    private void restrictFilePermissions() {
        try {
            tokenFile.toFile().setReadable(false, false);
            tokenFile.toFile().setReadable(true, true);
            tokenFile.toFile().setWritable(false, false);
            tokenFile.toFile().setWritable(true, true);
        } catch (Exception ignored) {
            // Best-effort, don't fail if permission setting fails (e.g., Windows)
        }
    }
}

