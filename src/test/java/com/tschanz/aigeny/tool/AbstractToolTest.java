package com.tschanz.aigeny.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbstractTool}.
 */
@DisplayName("AbstractTool")
class AbstractToolTest {

    /** Minimal concrete subclass used for testing. */
    private static class StubTool extends AbstractTool {

        private final String name;

        StubTool(ObjectMapper objectMapper, String name) {
            super(objectMapper);
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return "stub"; }
        @Override public ToolDefinition getDefinition() { return null; }
        @Override public ToolResult execute(String argumentsJson) { return new ToolResult("ok"); }
    }

    private AbstractTool tool;

    @BeforeEach
    void setUp() {
        tool = new StubTool(new ObjectMapper(), "my_tool");
    }

    @Nested
    @DisplayName("getCallDescription default implementation")
    class GetCallDescription {

        @Test
        @DisplayName("returns the 'description' field value when present and non-blank")
        void returnsDescriptionField() {
            String result = tool.getCallDescription("{\"description\": \"Fetching user data\"}");
            assertThat(result).isEqualTo("Fetching user data");
        }

        @Test
        @DisplayName("falls back to getName() when 'description' field is absent")
        void fallsBackToNameWhenDescriptionAbsent() {
            String result = tool.getCallDescription("{\"sql\": \"SELECT 1\"}");
            assertThat(result).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("falls back to getName() when 'description' field is blank")
        void fallsBackToNameWhenDescriptionBlank() {
            String result = tool.getCallDescription("{\"description\": \"   \"}");
            assertThat(result).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("falls back to getName() when 'description' field is null JSON")
        void fallsBackToNameWhenDescriptionNull() {
            String result = tool.getCallDescription("{\"description\": null}");
            assertThat(result).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("falls back to getName() when argumentsJson is empty object")
        void fallsBackToNameWhenEmptyJson() {
            String result = tool.getCallDescription("{}");
            assertThat(result).isEqualTo("my_tool");
        }

        @Test
        @DisplayName("falls back to getName() when argumentsJson is invalid JSON")
        void fallsBackToNameWhenInvalidJson() {
            String result = tool.getCallDescription("not-json");
            assertThat(result).isEqualTo("my_tool");
        }
    }

    @Nested
    @DisplayName("requiresConfirmation default")
    class RequiresConfirmation {

        @Test
        @DisplayName("returns false by default")
        void returnsFalseByDefault() {
            assertThat(tool.requiresConfirmation()).isFalse();
        }
    }

    @Nested
    @DisplayName("objectMapper is accessible to subclasses")
    class ObjectMapperAccess {

        @Test
        @DisplayName("objectMapper field is not null")
        void objectMapperIsNotNull() {
            assertThat(tool.objectMapper).isNotNull();
        }
    }
}
