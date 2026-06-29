package com.tschanz.aigeny.database;

import com.tschanz.aigeny.config.ConfigurationValidator;
import com.tschanz.aigeny.database.DbConfiguration;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.sql.*;

/**
 * Counts accessible Oracle tables on startup (and on demand via /api/schema/reload).
 * The table count is shown in the status bar of the UI.
 *
 * Note: The schema is no longer pre-loaded into the LLM system prompt.
 * The agent discovers tables/columns on demand via the query_oracle_db tool.
 */
@Service
public class SchemaLoader {

    private static final Logger log = LoggerFactory.getLogger(SchemaLoader.class);

    private final DbConfiguration dbConfig;
    private final ConfigurationValidator configValidator;
    private final Environment env;
    private volatile int tableCount = 0;
    private boolean dbReachable = false;

    public SchemaLoader(DbConfiguration dbConfig, ConfigurationValidator configValidator, Environment env) {
        this.dbConfig = dbConfig;
        this.configValidator = configValidator;
        this.env   = env;
    }

    /** Load table count automatically on startup if DB is configured. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (configValidator.isDbConfigured(dbConfig)) {
            try {
                reload();
            } catch (Exception e) {
                log.warn("Could not count DB tables on startup: {}", e.getMessage());
            }
        } else {
            log.info("DB not configured - skipping table count");
        }

        String port = env.getProperty("server.port", "8080");
        String url  = "http://localhost:" + port;
        System.out.println();
        System.out.println("==========================================");
        System.out.println("  AIgeny is ready!");
        System.out.println("  Open a browser at " + url);
        System.out.println("==========================================");
        System.out.println();
    }

    /** Reconnects and refreshes the table count. */
    public void reload() throws Exception {
        if (!configValidator.isDbConfigured(dbConfig)) {
            tableCount = 0;
            dbReachable = false;
            return;
        }
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(dbConfig.getUrl());
        hc.setUsername(dbConfig.getUsername());
        hc.setPassword(dbConfig.getPassword());
        hc.setMaximumPoolSize(1);
        hc.setConnectionTimeout(10_000);
        hc.setReadOnly(true);
        hc.setPoolName("AIgeny-Schema");
        // If a dedicated schema is configured (different from the login user),
        // switch the Oracle session schema for accurate table counting.
        String effectiveSchema = dbConfig.getEffectiveSchema();
        if (!effectiveSchema.isBlank() && !effectiveSchema.equalsIgnoreCase(dbConfig.getUsername())) {
            hc.setConnectionInitSql("ALTER SESSION SET CURRENT_SCHEMA = " + effectiveSchema);
            log.info("SchemaLoader: CURRENT_SCHEMA will be set to {}", effectiveSchema);
        }

        try (HikariDataSource ds = new HikariDataSource(hc);
             Connection conn = ds.getConnection()) {
            tableCount = countTables(conn, effectiveSchema);
            dbReachable = true;
            log.info("DB reachable - {} accessible tables found", tableCount);
        }
    }

    public int getTableCount() { return tableCount; }

    private int countTables(Connection conn, String schema) {
        // Filter by owner when a schema is known, so the count reflects only that schema.
        String ownerFilter = (schema != null && !schema.isBlank())
                ? " AND owner = '" + schema.toUpperCase() + "'" : "";
        String sql = """
            SELECT COUNT(*) FROM all_tables
            WHERE tablespace_name = 'USERS'
            """ + ownerFilter + """
            \nAND table_name NOT LIKE 'HTE!_%' ESCAPE '!'
            AND table_name NOT LIKE 'WWV!_%' ESCAPE '!'
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.warn("Could not count all_tables, trying user_tables: {}", e.getMessage());
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM user_tables");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            } catch (SQLException ex) {
                log.warn("Could not count user_tables either: {}", ex.getMessage());
            }
        }
        return 0;
    }
}
