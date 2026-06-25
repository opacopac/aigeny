package com.tschanz.aigeny.orchestration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PromptBuilder.
 */
class PromptBuilderTest {

    @Nested
    class Construction {

        @Test
        void shouldLoadTemplateFromClasspath() throws IOException {
            // When
            PromptBuilder builder = new PromptBuilder();

            // Then
            assertThat(builder.getTemplate()).isNotNull();
            assertThat(builder.getTemplate()).isNotEmpty();
        }

        @Test
        void shouldAcceptCustomTemplate() {
            // Given
            String customTemplate = "This is a custom system prompt";

            // When
            PromptBuilder builder = new PromptBuilder(customTemplate);

            // Then
            assertThat(builder.getTemplate()).isEqualTo(customTemplate);
        }
    }

    @Nested
    class BuildSystemPrompt {

        @Test
        void shouldReturnTemplateAsSystemPrompt() {
            // Given
            String template = "You are a helpful AI assistant.";
            PromptBuilder builder = new PromptBuilder(template);

            // When
            String systemPrompt = builder.buildSystemPrompt();

            // Then
            assertThat(systemPrompt).isEqualTo(template);
        }

        @Test
        void shouldReturnSamePromptOnMultipleCalls() {
            // Given
            String template = "Test prompt";
            PromptBuilder builder = new PromptBuilder(template);

            // When
            String prompt1 = builder.buildSystemPrompt();
            String prompt2 = builder.buildSystemPrompt();

            // Then
            assertThat(prompt1).isEqualTo(prompt2);
            assertThat(prompt1).isEqualTo(template);
        }

        @Test
        void shouldHandleEmptyTemplate() {
            // Given
            PromptBuilder builder = new PromptBuilder("");

            // When
            String systemPrompt = builder.buildSystemPrompt();

            // Then
            assertThat(systemPrompt).isEmpty();
        }

        @Test
        void shouldHandleMultilineTemplate() {
            // Given
            String template = "Line 1\nLine 2\nLine 3";
            PromptBuilder builder = new PromptBuilder(template);

            // When
            String systemPrompt = builder.buildSystemPrompt();

            // Then
            assertThat(systemPrompt).contains("Line 1", "Line 2", "Line 3");
        }
    }

    @Nested
    class GetTemplate {

        @Test
        void shouldReturnOriginalTemplate() {
            // Given
            String template = "Original template";
            PromptBuilder builder = new PromptBuilder(template);

            // When
            String retrieved = builder.getTemplate();

            // Then
            assertThat(retrieved).isEqualTo(template);
        }
    }
}

