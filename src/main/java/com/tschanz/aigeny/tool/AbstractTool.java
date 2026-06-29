package com.tschanz.aigeny.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for all Tool implementations.
 *
 * <p>Provides the shared {@link ObjectMapper} via constructor injection so that concrete
 * tools no longer need their own static {@code new ObjectMapper()} instances.
 * The default {@link #getCallDescription(String)} reads a {@code description} field
 * from the arguments JSON and falls back to {@link #getName()} when absent.
 */
public abstract class AbstractTool implements Tool {

    protected final ObjectMapper objectMapper;

    protected AbstractTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Default implementation: reads the {@code description} field from {@code argumentsJson}
     * and returns it if non-blank; otherwise returns {@link #getName()}.
     * Concrete tools may override this for richer descriptions.
     */
    @Override
    public String getCallDescription(String argumentsJson) {
        try {
            JsonNode node = objectMapper.readTree(argumentsJson);
            JsonNode desc = node.get("description");
            if (desc != null && !desc.isNull() && !desc.asText().isBlank()) {
                return desc.asText();
            }
        } catch (Exception ignored) {}
        return getName();
    }
}
