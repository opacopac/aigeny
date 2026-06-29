package com.tschanz.aigeny.orchestration;

import com.tschanz.aigeny.llm.model.ToolCall;
import com.tschanz.aigeny.llm.model.ToolDefinition;
import com.tschanz.aigeny.tool.Tool;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ToolExecutor.
 */
class ToolExecutorTest {

    private Tool mockTool1;
    private Tool mockTool2;
    private List<Tool> tools;

    @BeforeEach
    void setUp() {
        mockTool1 = mock(Tool.class);
        when(mockTool1.getName()).thenReturn("tool1");
        when(mockTool1.getDefinition()).thenReturn(mock(ToolDefinition.class));

        mockTool2 = mock(Tool.class);
        when(mockTool2.getName()).thenReturn("tool2");
        when(mockTool2.getDefinition()).thenReturn(mock(ToolDefinition.class));

        tools = new ArrayList<>();
        tools.add(mockTool1);
        tools.add(mockTool2);
    }

    @Nested
    class Construction {

        @Test
        void shouldInitializeWithTools() {
            // When
            ToolExecutor executor = new ToolExecutor(tools);

            // Then
            assertThat(executor.getTools()).hasSize(2);
            assertThat(executor.getToolCount()).isEqualTo(2);
        }

        @Test
        void shouldHandleEmptyToolList() {
            // When
            ToolExecutor executor = new ToolExecutor(List.of());

            // Then
            assertThat(executor.getTools()).isEmpty();
            assertThat(executor.getToolCount()).isZero();
        }
    }

    @Nested
    class FindTool {

        @Test
        void shouldFindExistingTool() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            Optional<Tool> found = executor.findTool("tool1");

            // Then
            assertThat(found).isPresent();
            assertThat(found.get()).isEqualTo(mockTool1);
        }

        @Test
        void shouldReturnEmptyForNonExistingTool() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            Optional<Tool> found = executor.findTool("nonexistent");

            // Then
            assertThat(found).isEmpty();
        }

        @Test
        void shouldBeCaseSensitive() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            Optional<Tool> found = executor.findTool("TOOL1");

            // Then
            assertThat(found).isEmpty();
        }

        @Test
        void shouldFindSecondTool() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            Optional<Tool> found = executor.findTool("tool2");

            // Then
            assertThat(found).isPresent();
            assertThat(found.get()).isEqualTo(mockTool2);
        }
    }

    @Nested
    class ExecuteToolCall {

        @Test
        void shouldExecuteToolSuccessfully() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("tool1", "{}"));
            ToolResult expectedResult = new ToolResult("Success");
            when(mockTool1.execute(anyString())).thenReturn(expectedResult);
            when(mockTool1.getCallDescription(anyString())).thenReturn("Calling tool1");

            // When
            ToolResult result = executor.executeToolCall(toolCall, null);

            // Then
            assertThat(result).isEqualTo(expectedResult);
            verify(mockTool1).execute("{}");
        }

        @Test
        void shouldInvokeCallbackBeforeExecution() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("tool1", "{\"arg\":\"value\"}"));
            ToolResult expectedResult = new ToolResult("Success");
            when(mockTool1.execute(anyString())).thenReturn(expectedResult);
            when(mockTool1.getCallDescription(anyString())).thenReturn("Description");

            AtomicBoolean callbackInvoked = new AtomicBoolean(false);
            AtomicReference<String> capturedName = new AtomicReference<>();
            AtomicReference<String> capturedDesc = new AtomicReference<>();

            // When
            ToolResult result = executor.executeToolCall(toolCall, (name, desc) -> {
                callbackInvoked.set(true);
                capturedName.set(name);
                capturedDesc.set(desc);
            });

            // Then
            assertThat(callbackInvoked.get()).isTrue();
            assertThat(capturedName.get()).isEqualTo("tool1");
            assertThat(capturedDesc.get()).isEqualTo("Description");
            assertThat(result).isEqualTo(expectedResult);
        }

        @Test
        void shouldHandleUnknownTool() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("unknown", "{}"));

            // When
            ToolResult result = executor.executeToolCall(toolCall, null);

            // Then
            assertThat(result.getText()).contains("unknown");
            // Note: getName() is called during findTool, but execute() should not be called
            verify(mockTool1, never()).execute(anyString());
            verify(mockTool2, never()).execute(anyString());
        }

        @Test
        void shouldHandleToolExecutionException() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("tool1", "{}"));
            when(mockTool1.execute(anyString())).thenThrow(new RuntimeException("Tool failed"));
            when(mockTool1.getCallDescription(anyString())).thenReturn("Description");

            // When
            ToolResult result = executor.executeToolCall(toolCall, null);

            // Then
            assertThat(result.getText()).contains("tool1");
            assertThat(result.getText()).contains("Tool failed");
        }

        @Test
        void shouldNotInvokeCallbackForUnknownTool() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("unknown", "{}"));
            AtomicBoolean callbackInvoked = new AtomicBoolean(false);

            // When
            executor.executeToolCall(toolCall, (name, desc) -> {
                callbackInvoked.set(true);
            });

            // Then
            assertThat(callbackInvoked.get()).isFalse();
        }

        @Test
        void shouldWorkWithoutCallback() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("tool1", "{}"));
            ToolResult expectedResult = new ToolResult("Success");
            when(mockTool1.execute(anyString())).thenReturn(expectedResult);
            when(mockTool1.getCallDescription(anyString())).thenReturn("Description");

            // When
            ToolResult result = executor.executeToolCall(toolCall, null);

            // Then
            assertThat(result).isEqualTo(expectedResult);
        }
    }

    @Nested
    class GetTools {

        @Test
        void shouldReturnAllTools() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            List<Tool> allTools = executor.getTools();

            // Then
            assertThat(allTools).hasSize(2);
            assertThat(allTools).containsExactly(mockTool1, mockTool2);
        }

        @Test
        void shouldReturnEmptyListWhenNoTools() {
            // Given
            ToolExecutor executor = new ToolExecutor(List.of());

            // When
            List<Tool> allTools = executor.getTools();

            // Then
            assertThat(allTools).isEmpty();
        }
    }

    @Nested
    class GetToolCount {

        @Test
        void shouldReturnCorrectCount() {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);

            // When
            int count = executor.getToolCount();

            // Then
            assertThat(count).isEqualTo(2);
        }

        @Test
        void shouldReturnZeroWhenNoTools() {
            // Given
            ToolExecutor executor = new ToolExecutor(List.of());

            // When
            int count = executor.getToolCount();

            // Then
            assertThat(count).isZero();
        }
    }

    @Nested
    class CurrentToolCallContextManagement {

        @Test
        void shouldSetCurrentToolCallContextDuringExecution() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-ctx-test", new ToolCall.FunctionCall("tool1", "{}"));
            AtomicReference<String> capturedId = new AtomicReference<>();
            when(mockTool1.getCallDescription(anyString())).thenReturn("desc");
            when(mockTool1.execute(anyString())).thenAnswer(inv -> {
                capturedId.set(CurrentToolCallContext.get());
                return new ToolResult("ok");
            });

            // When
            executor.executeToolCall(toolCall, null);

            // Then
            assertThat(capturedId.get()).isEqualTo("call-ctx-test");
        }

        @Test
        void shouldClearCurrentToolCallContextAfterExecution() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-1", new ToolCall.FunctionCall("tool1", "{}"));
            when(mockTool1.getCallDescription(anyString())).thenReturn("desc");
            when(mockTool1.execute(anyString())).thenReturn(new ToolResult("ok"));

            // When
            executor.executeToolCall(toolCall, null);

            // Then: context must be cleared after execution
            assertThat(CurrentToolCallContext.get()).isNull();
        }

        @Test
        void shouldClearCurrentToolCallContextEvenAfterException() throws Exception {
            // Given
            ToolExecutor executor = new ToolExecutor(tools);
            ToolCall toolCall = new ToolCall("call-err", new ToolCall.FunctionCall("tool1", "{}"));
            when(mockTool1.getCallDescription(anyString())).thenReturn("desc");
            when(mockTool1.execute(anyString())).thenThrow(new RuntimeException("boom"));

            // When
            executor.executeToolCall(toolCall, null); // error is swallowed, returns ToolResult

            // Then: context must still be cleared
            assertThat(CurrentToolCallContext.get()).isNull();
        }
    }
}






