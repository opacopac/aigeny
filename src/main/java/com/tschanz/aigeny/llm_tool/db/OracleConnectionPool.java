package com.tschanz.aigeny.llm_tool.db;

import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.config.DbConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Manages the lifecycle of the Oracle HikariCP connection pool as a dedicated
 * Spring component (Single Responsibility Principle, S-4 fix).
 *
 * <p>Pool creation is attempted once at application startup ({@link PostConstruct}).
 * If the database is not configured the pool stays {@code null} and
 * {@link #isConfigured()} returns {@code false}. The pool is closed cleanly on
 * application shutdown ({@link PreDestroy}) to prevent connection leaks.
 *
 * <p>{@link OracleDbTool} receives this component via constructor injection and
 * no longer manages the pool itself.
 */
@Component
public class OracleConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(OracleConnectionPool.class);

    private final DbConfiguration dbConfig;
    private final ConfigurationValidator configValidator;

    @Nullable
    private HikariDataSource dataSource;

    public OracleConnectionPool(DbConfiguration dbConfig, ConfigurationValidator configValidator) {
        this.dbConfig = dbConfig;
        this.configValidator = configValidator;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    void init() {
        if (!configValidator.isDbConfigured(dbConfig)) {
            log.info("Oracle DB not configured – connection pool not created.");
            return;
        }
        try {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(dbConfig.getUrl());
            hc.setUsername(dbConfig.getUsername());
            hc.setPassword(dbConfig.getPassword());
            hc.setMaximumPoolSize(3);
            hc.setConnectionTimeout(15_000);
            hc.setReadOnly(true);
            hc.setPoolName("AIgeny-Oracle");
            // If a dedicated schema is configured (different from the login user),
            // switch the Oracle session schema so unqualified table references work.
            String effectiveSchema = dbConfig.getEffectiveSchema();
            if (!effectiveSchema.isBlank() && !effectiveSchema.equalsIgnoreCase(dbConfig.getUsername())) {
                hc.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + effectiveSchema);
                log.info("Oracle pool: CURRENT_SCHEMA will be set to {}", effectiveSchema);
            }
            dataSource = new HikariDataSource(hc);
            log.info("Oracle connection pool created");
        } catch (Exception e) {
            log.error("Failed to create Oracle pool: {}", e.getMessage());
        }
    }

    @PreDestroy
    void destroy() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Oracle connection pool closed");
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the database is configured (URL + username present).
     * Does <em>not</em> guarantee that pool creation succeeded.
     */
    public boolean isConfigured() {
        return configValidator.isDbConfigured(dbConfig);
    }

    /**
     * Returns the active {@link DataSource}, or {@code null} if the database is
     * not configured or pool creation failed at startup.
     */
    @Nullable
    public DataSource getDataSource() {
        return dataSource;
    }
}

