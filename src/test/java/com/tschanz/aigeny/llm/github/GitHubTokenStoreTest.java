package com.tschanz.aigeny.llm.github;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitHubTokenStore")
class GitHubTokenStoreTest {

    @TempDir
    Path tempDir;

    private GitHubTokenStore tokenStore;
    private Path tokenFile;

    @BeforeEach
    void setUp() {
        tokenFile = tempDir.resolve("github-copilot.json");
        tokenStore = new GitHubTokenStore(tokenFile);
    }

    @Nested
    @DisplayName("Token Persistence")
    class TokenPersistence {

        @Test
        @DisplayName("should save token to file")
        void shouldSaveTokenToFile() throws IOException {
            // Given
            String token = "gho_test1234567890";

            // When
            tokenStore.save(token);

            // Then
            assertThat(tokenFile).exists();
            String content = Files.readString(tokenFile);
            assertThat(content).contains("gho_test1234567890");
            assertThat(content).contains("access_token");
            assertThat(content).contains("saved_at");
        }

        @Test
        @DisplayName("should load saved token")
        void shouldLoadSavedToken() {
            // Given
            String token = "gho_test_load_token";
            tokenStore.save(token);

            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isPresent();
            assertThat(loaded.get()).isEqualTo(token);
        }

        @Test
        @DisplayName("should return empty when no token file exists")
        void shouldReturnEmptyWhenNoFileExists() {
            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("should create parent directories when saving")
        void shouldCreateParentDirectories() {
            // Given
            Path nestedPath = tempDir.resolve("nested").resolve("dir").resolve("token.json");
            GitHubTokenStore nestedStore = new GitHubTokenStore(nestedPath);

            // When
            nestedStore.save("test_token");

            // Then
            assertThat(nestedPath).exists();
            assertThat(nestedPath.getParent()).exists();
        }

        @Test
        @DisplayName("should overwrite existing token")
        void shouldOverwriteExistingToken() {
            // Given
            tokenStore.save("old_token");
            String newToken = "new_token_123";

            // When
            tokenStore.save(newToken);
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isPresent();
            assertThat(loaded.get()).isEqualTo(newToken);
        }
    }

    @Nested
    @DisplayName("Token Deletion")
    class TokenDeletion {

        @Test
        @DisplayName("should delete existing token file")
        void shouldDeleteExistingTokenFile() {
            // Given
            tokenStore.save("test_token");
            assertThat(tokenFile).exists();

            // When
            tokenStore.delete();

            // Then
            assertThat(tokenFile).doesNotExist();
        }

        @Test
        @DisplayName("should not throw when deleting non-existent file")
        void shouldNotThrowWhenDeletingNonExistentFile() {
            // When/Then
            assertThat(tokenFile).doesNotExist();
            tokenStore.delete();  // Should not throw
        }

        @Test
        @DisplayName("should return empty after deletion")
        void shouldReturnEmptyAfterDeletion() {
            // Given
            tokenStore.save("test_token");
            tokenStore.delete();

            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("File Existence Check")
    class FileExistenceCheck {

        @Test
        @DisplayName("should return true when token file exists")
        void shouldReturnTrueWhenFileExists() {
            // Given
            tokenStore.save("test_token");

            // When
            boolean exists = tokenStore.exists();

            // Then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when token file does not exist")
        void shouldReturnFalseWhenFileDoesNotExist() {
            // When
            boolean exists = tokenStore.exists();

            // Then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("File Path Access")
    class FilePathAccess {

        @Test
        @DisplayName("should return correct token file path")
        void shouldReturnCorrectTokenFilePath() {
            // When
            Path path = tokenStore.getTokenFilePath();

            // Then
            assertThat(path).isEqualTo(tokenFile);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle token with special characters")
        void shouldHandleTokenWithSpecialCharacters() {
            // Given
            String specialToken = "gho_test/with:special@chars!";

            // When
            tokenStore.save(specialToken);
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isPresent();
            assertThat(loaded.get()).isEqualTo(specialToken);
        }

        @Test
        @DisplayName("should handle very long tokens")
        void shouldHandleVeryLongTokens() {
            // Given
            String longToken = "gho_" + "x".repeat(500);

            // When
            tokenStore.save(longToken);
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isPresent();
            assertThat(loaded.get()).isEqualTo(longToken);
        }

        @Test
        @DisplayName("should return empty for corrupted JSON file")
        void shouldReturnEmptyForCorruptedJson() throws IOException {
            // Given
            Files.writeString(tokenFile, "{ invalid json }");

            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("should return empty for JSON without access_token field")
        void shouldReturnEmptyForJsonWithoutAccessToken() throws IOException {
            // Given
            Files.writeString(tokenFile, "{\"other_field\": \"value\"}");

            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("should return empty for blank token in JSON")
        void shouldReturnEmptyForBlankToken() throws IOException {
            // Given
            Files.writeString(tokenFile, "{\"access_token\": \"   \"}");

            // When
            Optional<String> loaded = tokenStore.load();

            // Then
            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should use default path in user home directory")
        void shouldUseDefaultPathInUserHome() {
            // Given
            GitHubTokenStore defaultStore = new GitHubTokenStore();

            // When
            Path path = defaultStore.getTokenFilePath();

            // Then
            assertThat(path).isNotNull();
            assertThat(path.toString()).contains(".aigeny");
            assertThat(path.toString()).contains("github-copilot.json");
        }
    }
}

