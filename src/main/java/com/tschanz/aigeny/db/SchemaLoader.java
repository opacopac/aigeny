package com.tschanz.aigeny.db;

import com.tschanz.aigeny.config.AigenyProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    private final AigenyProperties props;
    private volatile int tableCount = 0;
    private boolean dbReachable = false;

    public SchemaLoader(AigenyProperties props) {
        this.props = props;
    }

    /** Load table count automatically on startup if DB is configured. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (props.isDbConfigured()) {
            try {
                reload();
            } catch (Exception e) {
                log.warn("Could not count DB tables on startup: {}", e.getMessage());
            }
        } else {
            log.info("DB not configured - skipping table count");
        }
    }

    /** Reconnects and refreshes the table count. */
    public void reload() throws Exception {
        if (!props.isDbConfigured()) {
            tableCount = 0;
            dbReachable = false;
            return;
        }
        AigenyProperties.Db db = props.getDb();
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(db.getUrl());
        hc.setUsername(db.getUsername());
        hc.setPassword(db.getPassword());
        hc.setMaximumPoolSize(1);
        hc.setConnectionTimeout(10_000);
        hc.setReadOnly(true);
        hc.setPoolName("AIgeny-Schema");

        try (HikariDataSource ds = new HikariDataSource(hc);
             Connection conn = ds.getConnection()) {
            tableCount = countTables(conn);
            dbReachable = true;
            log.info("DB reachable - {} accessible tables found", tableCount);
        }
    }

    public int getTableCount() { return tableCount; }

    private int countTables(Connection conn) {
        String sql = """
            SELECT COUNT(*) FROM all_tables
            WHERE tablespace_name = 'USERS'
            AND table_name NOT LIKE 'HTE!_%' ESCAPE '!'
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
