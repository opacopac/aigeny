package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm_tool.bitbucket.BitbucketTokenContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BitbucketContextProvider}.
 */
@DisplayName("BitbucketContextProvider")
class BitbucketContextProviderTest {

    private BitbucketContextProvider provider;

    @BeforeEach
    void setUp() {
        provider = new BitbucketContextProvider();
    }

    @AfterEach
    void tearDown() {
        provider.cleanup();
    }

    @Test
    @DisplayName("getKey() returns 'bitbucket'")
    void getKey_returnsBitbucket() {
        assertThat(provider.getKey()).isEqualTo("bitbucket");
        assertThat(provider.getKey()).isEqualTo(BitbucketContextProvider.KEY);
    }

    // ── setup ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setup()")
    class Setup {

        @Test
        @DisplayName("sets BitbucketTokenContext to provided token")
        void setsToken() {
            provider.setup("bb-token-xyz", false);

            assertThat(BitbucketTokenContext.get()).isEqualTo("bb-token-xyz");
        }

        @Test
        @DisplayName("sets BitbucketTokenContext to null when token is null")
        void setsNullToken() {
            provider.setup(null, false);

            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("writeEnabled flag is silently ignored")
        void writeEnabledIsIgnored() {
            // Bitbucket does not have a write-mode concept – calling with either value must work
            provider.setup("token", true);
            assertThat(BitbucketTokenContext.get()).isEqualTo("token");

            provider.cleanup();

            provider.setup("token2", false);
            assertThat(BitbucketTokenContext.get()).isEqualTo("token2");
        }
    }

    // ── cleanup ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cleanup()")
    class Cleanup {

        @Test
        @DisplayName("clears BitbucketTokenContext")
        void clearsToken() {
            BitbucketTokenContext.set("some-token");

            provider.cleanup();

            assertThat(BitbucketTokenContext.get()).isNull();
        }

        @Test
        @DisplayName("cleanup is idempotent – calling twice does not throw")
        void cleanupIsIdempotent() {
            provider.setup("token", false);
            provider.cleanup();
            provider.cleanup(); // should not throw
        }

        @Test
        @DisplayName("cleanup on already-clean state does not throw")
        void cleanupOnCleanState() {
            provider.cleanup(); // nothing was set – should not throw
        }
    }

    // ── setup → cleanup cycle ─────────────────────────────────────────────────

    @Nested
    @DisplayName("setup-cleanup cycle")
    class Cycle {

        @Test
        @DisplayName("supports multiple setup-cleanup cycles")
        void supportsMultipleCycles() {
            provider.setup("token-1", false);
            assertThat(BitbucketTokenContext.get()).isEqualTo("token-1");
            provider.cleanup();
            assertThat(BitbucketTokenContext.get()).isNull();

            provider.setup("token-2", true);
            assertThat(BitbucketTokenContext.get()).isEqualTo("token-2");
            provider.cleanup();
            assertThat(BitbucketTokenContext.get()).isNull();
        }
    }
}

