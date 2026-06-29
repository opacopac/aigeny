package com.tschanz.aigeny.database;

import com.tschanz.aigeny.database.SchemaLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SchemaController}.
 *
 * <p>Covers: POST /api/schema/reload – success and failure paths.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaController")
class SchemaControllerTest {

    @Mock private SchemaLoader schemaLoader;

    private SchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new SchemaController(schemaLoader);
    }

    // ── POST /api/schema/reload ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/schema/reload")
    class ReloadSchema {

        @Test
        @DisplayName("calls SchemaLoader.reload() and returns status=ok with table count")
        void successReturnsOkWithTableCount() throws Exception {
            when(schemaLoader.getTableCount()).thenReturn(42);

            ResponseEntity<Map<String, Object>> response =
                    controller.reloadSchema().get();

            verify(schemaLoader).reload();
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "ok");
            assertThat(response.getBody()).containsEntry("tables", 42);
        }

        @Test
        @DisplayName("returns status=error with message when SchemaLoader.reload() throws")
        void failureReturnsErrorStatus() throws Exception {
            doThrow(new RuntimeException("DB unreachable")).when(schemaLoader).reload();

            ResponseEntity<Map<String, Object>> response =
                    controller.reloadSchema().get();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("status", "error");
            assertThat(response.getBody()).containsEntry("error", "DB unreachable");
        }

        @Test
        @DisplayName("does not propagate exception to caller – always returns a response")
        void neverThrowsToCallerOnFailure() throws Exception {
            doThrow(new RuntimeException("boom")).when(schemaLoader).reload();

            assertThat(controller.reloadSchema())
                    .succeedsWithin(java.time.Duration.ofSeconds(2));
        }
    }
}


