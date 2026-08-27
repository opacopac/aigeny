package com.tschanz.aigeny.database.mcp_server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OracleSqlSupport")
class OracleSqlSupportTest {

    @Test
    @DisplayName("toText() returns placeholder for empty rows")
    void emptyRows() {
        assertThat(OracleSqlSupport.toText(List.of("ID"), List.of()))
                .isEqualTo("(no rows returned)");
    }

    @Test
    @DisplayName("toText() renders column header and row values, NULL for missing values")
    void rendersRows() {
        String text = OracleSqlSupport.toText(
                List.of("ID", "NAME"),
                List.of(
                        new java.util.LinkedHashMap<>(Map.of("ID", 1, "NAME", "Alice")),
                        new java.util.LinkedHashMap<>() {{ put("ID", 2); put("NAME", null); }}
                ));

        assertThat(text).contains("ID | NAME");
        assertThat(text).contains("Alice");
        assertThat(text).contains("NULL");
    }

    @Test
    @DisplayName("clamp() bounds a value between min and max")
    void clamps() {
        assertThat(OracleSqlSupport.clamp(10, 1, 5)).isEqualTo(5);
        assertThat(OracleSqlSupport.clamp(-1, 1, 5)).isEqualTo(1);
        assertThat(OracleSqlSupport.clamp(3, 1, 5)).isEqualTo(3);
    }

    @Test
    @DisplayName("intArg() falls back to default for missing/unparsable values")
    void intArgFallback() {
        assertThat(OracleSqlSupport.intArg(Map.of(), "limit", 42)).isEqualTo(42);
        assertThat(OracleSqlSupport.intArg(Map.of("limit", "not-a-number"), "limit", 42)).isEqualTo(42);
        assertThat(OracleSqlSupport.intArg(Map.of("limit", 7), "limit", 42)).isEqualTo(7);
    }
}

