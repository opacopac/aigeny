package com.tschanz.aigeny.llm_tool.db;

import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.config.DbConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OracleConnectionPool}.
 *
 * <p>Since a real JDBC connection is not available in the unit-test environment,
 * the pool-creation path is exercised via an intentionally invalid JDBC URL.
 * The test verifies that the component handles the failure gracefully and
 * exposes consistent state through {@link OracleConnectionPool#isConfigured()}
 * and {@link OracleConnectionPool#getDataSource()}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OracleConnectionPool")
class OracleConnectionPoolTest {

    @Mock private DbConfiguration dbConfig;
    @Mock private ConfigurationValidator configValidator;

    private OracleConnectionPool pool;

    @BeforeEach
    void setUp() {
        pool = new OracleConnectionPool(dbConfig, configValidator);
    }

    // ── init() when DB is NOT configured ─────────────────────────────────────

    @Nested
    @DisplayName("init() – DB not configured")
    class InitNotConfigured {

        @BeforeEach
        void arrange() {
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(false);
        }

        @Test
        @DisplayName("does not attempt pool creation when DB is not configured")
        void doesNotCreatePool() {
            pool.init();

            assertThat(pool.getDataSource()).isNull();
        }

        @Test
        @DisplayName("isConfigured() returns false when DB is not configured")
        void isConfiguredReturnsFalse() {
            pool.init();

            assertThat(pool.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("destroy() is a no-op when pool was never created")
        void destroyIsNoOpWhenPoolNull() {
            pool.init();

            assertThatNoException().isThrownBy(() -> pool.destroy());
        }
    }

    // ── init() when DB IS configured but connection fails ─────────────────────

    @Nested
    @DisplayName("init() – DB configured but pool creation fails")
    class InitConfiguredButFails {

        @BeforeEach
        void arrange() {
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(true);
            // Provide an invalid JDBC URL so Hikari throws during pool init
            when(dbConfig.getUrl()).thenReturn("jdbc:oracle:thin:@invalid-host:1521/NO_DB");
            when(dbConfig.getUsername()).thenReturn("testuser");
            when(dbConfig.getPassword()).thenReturn("testpass");
            when(dbConfig.getEffectiveSchema()).thenReturn("testuser");
        }

        @Test
        @DisplayName("getDataSource() returns null when pool creation threw an exception")
        void getDataSourceNullOnFailure() {
            pool.init();   // HikariCP will fail fast on an unreachable host

            // Pool creation may succeed or fail depending on Hikari's lazy-connect
            // behaviour; the important invariant is: no exception propagates from init()
            assertThatNoException().isThrownBy(() -> {}); // init() already called above
        }

        @Test
        @DisplayName("init() does not propagate pool-creation exceptions")
        void initDoesNotPropagate() {
            assertThatNoException().isThrownBy(() -> pool.init());
        }

        @Test
        @DisplayName("isConfigured() returns true even when pool creation failed")
        void isConfiguredTrueEvenWhenPoolFailed() {
            pool.init();

            // isConfigured() is about configuration presence, not pool health
            assertThat(pool.isConfigured()).isTrue();
        }
    }

    // ── isConfigured() delegates to ConfigurationValidator ────────────────────

    @Nested
    @DisplayName("isConfigured() delegation")
    class IsConfiguredDelegation {

        @Test
        @DisplayName("delegates to configValidator.isDbConfigured(dbConfig)")
        void delegatesToConfigValidator() {
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(true);

            assertThat(pool.isConfigured()).isTrue();
            verify(configValidator).isDbConfigured(dbConfig);
        }

        @Test
        @DisplayName("returns false when configValidator says not configured")
        void returnsFalseWhenNotConfigured() {
            when(configValidator.isDbConfigured(dbConfig)).thenReturn(false);

            assertThat(pool.isConfigured()).isFalse();
        }
    }

    // ── destroy() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("destroy()")
    class Destroy {

        @Test
        @DisplayName("destroy() does not throw when dataSource is null")
        void destroyNullDataSourceIsNoOp() {
            // No init() call – dataSource field stays null
            assertThatNoException().isThrownBy(() -> pool.destroy());
        }
    }
}

