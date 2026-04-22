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
import java.util.*;

/**
 * Loads and caches the Oracle DB schema on startup (and on demand).
 * The schema string is injected into the LLM system prompt.
 */
@Service
public class SchemaLoader {

    private static final Logger log = LoggerFactory.getLogger(SchemaLoader.class);

    private final AigenyProperties props;
    private volatile String cachedSchema = "";
    private volatile int tableCount = 0;

    public SchemaLoader(AigenyProperties props) {
        this.props = props;
    }

    /** Load schema automatically on startup if DB is configured. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        if (props.isDbConfigured()) {
            try {
                reload();
            } catch (Exception e) {
                log.warn("Could not load DB schema on startup: {}", e.getMessage());
                cachedSchema = "(DB schema unavailable: " + e.getMessage() + ")";
            }
        } else {
            log.info("DB not configured — skipping schema load");
            cachedSchema = "";
        }
    }

    /** Force a schema reload (e.g. via REST endpoint). */
    public String reload() throws Exception {
        if (!props.isDbConfigured()) {
            cachedSchema = "";
            return cachedSchema;
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
            cachedSchema = buildSchemaText(conn, db.getUsername().toUpperCase());
            log.info("DB schema loaded ({} chars, ~{} tables)", cachedSchema.length(), tableCount);
        }
        return cachedSchema;
    }

    public String getSchema()   { return cachedSchema; }
    public int    getTableCount() { return tableCount; }

    // ── Private helpers ────────────────────────────────────────────────────

    private String buildSchemaText(Connection conn, String schemaUser) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ORACLE DATABASE SCHEMA ===\n");
        sb.append("Connected as: ").append(schemaUser).append("\n\n");

        String tableQuery = """
            SELECT owner, table_name, num_rows
            FROM all_tables
            WHERE owner NOT IN (
                'SYS','SYSTEM','OUTLN','DBSNMP','APPQOSSYS','WMSYS',
                'EXFSYS','CTXSYS','XDB','ORDDATA','ORDSYS','MDSYS',
                'OLAPSYS','OWBSYS','FLOWS_FILES'
            )
            ORDER BY owner, table_name
            FETCH FIRST 300 ROWS ONLY
            """;

        Map<String, List<String>> tablesByOwner = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(tableQuery);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String owner = rs.getString("owner");
                String table = rs.getString("table_name");
                long numRows = rs.getLong("num_rows");
                tablesByOwner.computeIfAbsent(owner, k -> new ArrayList<>())
                        .add(table + (numRows > 0 ? " (~" + numRows + " rows)" : ""));
            }
        } catch (SQLException e) {
            log.warn("Could not query all_tables: {}", e.getMessage());
            return loadUserSchema(conn, schemaUser);
        }

        if (tablesByOwner.isEmpty()) return loadUserSchema(conn, schemaUser);

        tableCount = tablesByOwner.values().stream().mapToInt(List::size).sum();

        for (Map.Entry<String, List<String>> entry : tablesByOwner.entrySet()) {
            String owner = entry.getKey();
            sb.append("SCHEMA: ").append(owner).append("\n");
            sb.append("-".repeat(50)).append("\n");
            for (String tableInfo : entry.getValue()) {
                String tableName = tableInfo.split(" ")[0];
                sb.append("  TABLE: ").append(owner).append(".").append(tableName)
                  .append("  ").append(tableInfo.contains("(") ? tableInfo.substring(tableInfo.indexOf("(") - 1) : "")
                  .append("\n");
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT column_name, data_type, nullable FROM all_columns " +
                        "WHERE owner = ? AND table_name = ? ORDER BY column_id")) {
                    ps.setString(1, owner);
                    ps.setString(2, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            sb.append("    ").append(rs.getString("column_name"))
                              .append(" ").append(rs.getString("data_type"))
                              .append("N".equals(rs.getString("nullable")) ? " NOT NULL" : "")
                              .append("\n");
                        }
                    }
                } catch (SQLException e) {
                    sb.append("    (columns unavailable)\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String loadUserSchema(Connection conn, String schemaUser) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append("=== DATABASE SCHEMA (USER TABLES) ===\n\n");
        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_name FROM user_tables ORDER BY table_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                count++;
                String table = rs.getString("table_name");
                sb.append("TABLE: ").append(schemaUser).append(".").append(table).append("\n");
                try (PreparedStatement cp = conn.prepareStatement(
                        "SELECT column_name, data_type, nullable FROM user_tab_columns " +
                        "WHERE table_name = ? ORDER BY column_id")) {
                    cp.setString(1, table);
                    try (ResultSet cr = cp.executeQuery()) {
                        while (cr.next()) {
                            sb.append("  ").append(cr.getString("column_name"))
                              .append(" ").append(cr.getString("data_type"))
                              .append("N".equals(cr.getString("nullable")) ? " NOT NULL" : "")
                              .append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
        }
        tableCount = count;
        return sb.toString();
    }
}
