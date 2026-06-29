package com.tschanz.aigeny.web;

import com.tschanz.aigeny.db.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for database schema operations.
 *
 * <p>Owns: POST /api/schema/reload
 *
 * <p>Separated from {@link ChatController} to give each class a single
 * responsibility (S-1 refactoring).
 */
@RestController
@RequestMapping("/api")
public class SchemaController {

    private static final Logger log = LoggerFactory.getLogger(SchemaController.class);

    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR  = "error";
    private static final String KEY_TABLES = "tables";
    private static final String VAL_OK     = "ok";
    private static final String VAL_ERROR  = "error";

    private final SchemaLoader schemaLoader;

    public SchemaController(SchemaLoader schemaLoader) {
        this.schemaLoader = schemaLoader;
    }

    // ── POST /api/schema/reload ──────────────────────────────────────────────

    @PostMapping("/schema/reload")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> reloadSchema() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                schemaLoader.reload();
                return ResponseEntity.ok(Map.of(
                        KEY_STATUS, VAL_OK,
                        KEY_TABLES, schemaLoader.getTableCount()
                ));
            } catch (Exception e) {
                log.error("Schema reload failed", e);
                return ResponseEntity.ok(Map.of(KEY_STATUS, VAL_ERROR, KEY_ERROR, e.getMessage()));
            }
        });
    }
}

