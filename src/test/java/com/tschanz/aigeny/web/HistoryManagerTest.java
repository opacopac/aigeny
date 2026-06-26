package com.tschanz.aigeny.web;

import com.tschanz.aigeny.llm.model.Message;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoryManager")
class HistoryManagerTest {

    @Mock
    private HttpSession session;

    private HistoryManager historyManager;

    @BeforeEach
    void setUp() {
        historyManager = new HistoryManager();
        lenient().when(session.getId()).thenReturn("session123");
    }

    @Nested
    @DisplayName("getOrCreateHistory")
    class GetOrCreateHistory {

        @Test
        @DisplayName("should create new history when none exists")
        void shouldCreateNewHistoryWhenNoneExists() {
            // Arrange
            when(session.getAttribute("chatHistory")).thenReturn(null);

            // Act
            List<Message> history = historyManager.getOrCreateHistory(session);

            // Assert
            assertThat(history).isNotNull().isEmpty();
            verify(session).setAttribute(eq("chatHistory"), any(ArrayList.class));
        }

        @Test
        @DisplayName("should return existing history when available")
        void shouldReturnExistingHistoryWhenAvailable() {
            // Arrange
            List<Message> existingHistory = new ArrayList<>();
            existingHistory.add(Message.user("test message"));
            when(session.getAttribute("chatHistory")).thenReturn(existingHistory);

            // Act
            List<Message> history = historyManager.getOrCreateHistory(session);

            // Assert
            assertThat(history).isSameAs(existingHistory);
            assertThat(history).hasSize(1);
            verify(session, never()).setAttribute(anyString(), any());
        }

        @Test
        @DisplayName("should return mutable list")
        void shouldReturnMutableList() {
            // Arrange
            when(session.getAttribute("chatHistory")).thenReturn(null);

            // Act
            List<Message> history = historyManager.getOrCreateHistory(session);

            // Assert - should be able to modify the list
            assertThat(history).isNotNull();
            history.add(Message.user("new message"));
            assertThat(history).hasSize(1);
        }

        @Test
        @DisplayName("should store created history in session")
        void shouldStoreCreatedHistoryInSession() {
            // Arrange
            when(session.getAttribute("chatHistory")).thenReturn(null);

            // Act
            List<Message> history = historyManager.getOrCreateHistory(session);
            history.add(Message.user("message 1"));
            history.add(Message.assistant("response 1"));

            // Assert
            verify(session).setAttribute(eq("chatHistory"), any(ArrayList.class));
            assertThat(history).hasSize(2);
        }

        @Test
        @DisplayName("should handle multiple calls consistently")
        void shouldHandleMultipleCallsConsistently() {
            // Arrange
            List<Message> storedHistory = new ArrayList<>();
            when(session.getAttribute("chatHistory"))
                .thenReturn(null)
                .thenReturn(storedHistory);

            // Act
            List<Message> history1 = historyManager.getOrCreateHistory(session);
            List<Message> history2 = historyManager.getOrCreateHistory(session);

            // Assert
            verify(session, times(1)).setAttribute(eq("chatHistory"), any(ArrayList.class));
            assertThat(history2).isSameAs(storedHistory);
        }
    }

    @Nested
    @DisplayName("clearHistory")
    class ClearHistory {

        @Test
        @DisplayName("should remove history attribute from session")
        void shouldRemoveHistoryAttributeFromSession() {
            // Act
            historyManager.clearHistory(session);

            // Assert
            verify(session).removeAttribute("chatHistory");
        }

        @Test
        @DisplayName("should handle clearing when no history exists")
        void shouldHandleClearingWhenNoHistoryExists() {
            // Act
            historyManager.clearHistory(session);

            // Assert
            verify(session).removeAttribute("chatHistory");
        }

        @Test
        @DisplayName("should clear existing history")
        void shouldClearExistingHistory() {
            // Act
            historyManager.clearHistory(session);

            // Assert
            verify(session).removeAttribute("chatHistory");
        }

        @Test
        @DisplayName("should be idempotent")
        void shouldBeIdempotent() {
            // Act
            historyManager.clearHistory(session);
            historyManager.clearHistory(session);
            historyManager.clearHistory(session);

            // Assert - should be called multiple times without error
            verify(session, times(3)).removeAttribute("chatHistory");
        }
    }

    @Nested
    @DisplayName("Complete Workflow")
    class CompleteWorkflow {

        @Test
        @DisplayName("should support full conversation lifecycle")
        void shouldSupportFullConversationLifecycle() {
            // Arrange
            List<Message> storedHistory = new ArrayList<>();
            when(session.getAttribute("chatHistory"))
                .thenReturn(null)
                .thenReturn(storedHistory)
                .thenReturn(storedHistory);

            // Act - Create and populate history
            List<Message> history = historyManager.getOrCreateHistory(session);
            history.add(Message.user("Hello"));
            history.add(Message.assistant("Hi there!"));
            
            // Simulate session storing the list
            storedHistory.addAll(history);

            // Retrieve history again
            List<Message> retrievedHistory = historyManager.getOrCreateHistory(session);
            assertThat(retrievedHistory).hasSize(2);

            // Clear history
            historyManager.clearHistory(session);

            // Assert
            verify(session, times(1)).setAttribute(eq("chatHistory"), any(ArrayList.class));
            verify(session, times(1)).removeAttribute("chatHistory");
        }

        @Test
        @DisplayName("should support create-clear-create cycle")
        void shouldSupportCreateClearCreateCycle() {
            // Arrange
            List<Message> firstHistory = new ArrayList<>();
            List<Message> secondHistory = new ArrayList<>();
            
            when(session.getAttribute("chatHistory"))
                .thenReturn(null)           // First getOrCreate
                .thenReturn(null);          // Second getOrCreate after clear

            // Act
            List<Message> history1 = historyManager.getOrCreateHistory(session);
            history1.add(Message.user("First conversation"));
            
            historyManager.clearHistory(session);
            
            List<Message> history2 = historyManager.getOrCreateHistory(session);
            history2.add(Message.user("Second conversation"));

            // Assert
            verify(session, times(2)).setAttribute(eq("chatHistory"), any(ArrayList.class));
            verify(session, times(1)).removeAttribute("chatHistory");
        }
    }
}
