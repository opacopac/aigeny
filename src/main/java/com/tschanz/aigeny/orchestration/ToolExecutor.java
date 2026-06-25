package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.Messages;
import com.tschanz.aigeny.llm.model.ToolCall;
import com.tschanz.aigeny.llm_tool.Tool;
import com.tschanz.aigeny.llm_tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Responsible for tool discovery and execution.
 * Single Responsibility: Tool management and execution logic.
 */
@Service
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private static final String MSG_UNKNOWN_TOOL = "orchestration.error.unknown_tool";
    private static final String MSG_TOOL_EXEC_FAILED = "orchestration.error.tool_execution";

    private final List<Tool> tools;

    /**
     * Constructor - Spring auto-collects all Tool beans into the list.
     *
     * @param tools list of available tools
     */
    public ToolExecutor(List<Tool> tools) {
        this.tools = tools;
        log.info("ToolExecutor initialized with {} tools: {}",
                tools.size(), tools.stream().map(Tool::getName).toList());
    }

    /**
     * Finds a tool by name.
     *
     * @param name the tool name
     * @return Optional containing the tool if found, empty otherwise
     */
    public Optional<Tool> findTool(String name) {
        return tools.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst();
    }

    /**
     * Executes a tool call.
     *
     * @param toolCall the tool call to execute
     * @param onToolCall optional callback invoked before execution with (toolName, description)
     * @return the tool result
     */
    public ToolResult executeToolCall(ToolCall toolCall, BiConsumer<String, String> onToolCall) {
        String toolName = toolCall.getFunction().getName();
        String toolArgs = toolCall.getFunction().getArguments();

        log.info("Tool call: {} args: {}", toolName, toolArgs);

        Optional<Tool> toolOpt = findTool(toolName);

        if (toolOpt.isEmpty()) {
            log.warn("Unknown tool: {}", toolName);
            return new ToolResult(Messages.get(MSG_UNKNOWN_TOOL, toolName));
        }

        Tool tool = toolOpt.get();

        // Notify callback before execution
        if (onToolCall != null) {
            onToolCall.accept(toolName, tool.getCallDescription(toolArgs));
        }

        try {
            ToolResult result = tool.execute(toolArgs);
            log.debug("Tool {} executed successfully", toolName);
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage(), e);
            return new ToolResult(Messages.get(MSG_TOOL_EXEC_FAILED, toolName, e.getMessage()));
        }
    }

    /**
     * Gets all available tools.
     *
     * @return list of tools
     */
    public List<Tool> getTools() {
        return tools;
    }

    /**
     * Gets the count of available tools.
     *
     * @return number of tools
     */
    public int getToolCount() {
        return tools.size();
    }
}

