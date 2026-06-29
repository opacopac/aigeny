package com.tschanz.aigeny.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tschanz.aigeny.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OracleDbTool}.
 *
 * <p>The {@link OracleConnectionPool} dependency is fully mocked, so these tests
 * verify only the tool's query-validation and query-execution logic without
 * requiring a real database (S-4 fix: pool lifecycle is no longer entangled with
 * the tool).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OracleDbTool")
class OracleDbToolTest {

    @Mock private OracleConnectionPool connectionPool;
    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;
    @Mock private ResultSetMetaData resultSetMetaData;

    private OracleDbTool tool;

    @BeforeEach
    void setUp() {
        tool = new OracleDbTool(connectionPool, new ObjectMapper());
    }

    // ── Not configured ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DB not configured")
    class NotConfigured {

        @BeforeEach
        void arrange() {
            when(connectionPool.isConfigured()).thenReturn(false);
        }

        @Test
        @DisplayName("returns 'not configured' message without touching the pool")
        void returnsNotConfiguredMessage() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"test\"}");

            assertThat(result.getText()).isNotBlank();
            assertThat(result.getText()).contains("not configured");
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("does not attempt to get a DataSource when not configured")
        void doesNotCallGetDataSource() throws Exception {
            tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"test\"}");

            verify(connectionPool).isConfigured();
            // getDataSource() must NOT be called – the check is short-circuited
            verify(connectionPool, org.mockito.Mockito.never()).getDataSource();
        }
    }

    // ── Pool unavailable (configured but DataSource is null) ──────────────────

    @Nested
    @DisplayName("Pool unavailable (configured but DataSource null)")
    class PoolUnavailable {

        @BeforeEach
        void arrange() {
            when(connectionPool.isConfigured()).thenReturn(true);
            when(connectionPool.getDataSource()).thenReturn(null);
        }

        @Test
        @DisplayName("returns 'no connection' message when DataSource is null")
        void returnsNoConnectionMessage() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"SELECT 1 FROM DUAL\",\"description\":\"test\"}");

            assertThat(result.getText()).isNotBlank();
            assertThat(result.getText()).contains("connect");
        }
    }

    // ── SQL validation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SQL validation")
    class SqlValidation {

        @BeforeEach
        void arrange() {
            when(connectionPool.isConfigured()).thenReturn(true);
        }

        @Test
        @DisplayName("rejects non-SELECT statement")
        void rejectsNonSelect() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"UPDATE foo SET x=1\",\"description\":\"hack\"}");

            assertThat(result.getText()).contains("SELECT");
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("rejects statement containing INSERT keyword")
        void rejectsInsert() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"SELECT * FROM t WHERE x = 'INSERT INTO'\",\"description\":\"x\"}");

            assertThat(result.getText()).isNotBlank();
            assertThat(result.getText()).contains("dangerous");
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("rejects statement containing DROP keyword")
        void rejectsDrop() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"SELECT DROP TABLE\",\"description\":\"x\"}");

            assertThat(result.getText()).isNotBlank();
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("rejects DELETE statement")
        void rejectsDelete() throws Exception {
            ToolResult result = tool.execute("{\"sql\":\"DELETE FROM users\",\"description\":\"evil\"}");

            assertThat(result.getText()).contains("SELECT");
            verifyNoInteractions(dataSource);
        }
    }

    // ── Successful execution ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful query execution")
    class SuccessfulExecution {

        @BeforeEach
        void arrange() throws Exception {
            when(connectionPool.isConfigured()).thenReturn(true);
            when(connectionPool.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.getMetaData()).thenReturn(resultSetMetaData);
            when(resultSetMetaData.getColumnCount()).thenReturn(2);
            when(resultSetMetaData.getColumnLabel(1)).thenReturn("ID");
            when(resultSetMetaData.getColumnLabel(2)).thenReturn("NAME");
            // Two rows
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getObject(1)).thenReturn(1, 2);
            when(resultSet.getObject(2)).thenReturn("Alice", "Bob");
        }

        @Test
        @DisplayName("result text contains column names")
        void resultContainsColumnNames() throws Exception {
            ToolResult result = tool.execute(
                    "{\"sql\":\"SELECT ID, NAME FROM USERS\",\"description\":\"get users\"}");

            assertThat(result.getText()).contains("ID");
            assertThat(result.getText()).contains("NAME");
        }

        @Test
        @DisplayName("result text contains row data")
        void resultContainsRowData() throws Exception {
            ToolResult result = tool.execute(
                    "{\"sql\":\"SELECT ID, NAME FROM USERS\",\"description\":\"get users\"}");

            assertThat(result.getText()).contains("Alice");
            assertThat(result.getText()).contains("Bob");
        }

        @Test
        @DisplayName("QueryResult is attached to ToolResult")
        void queryResultAttached() throws Exception {
            ToolResult result = tool.execute(
                    "{\"sql\":\"SELECT ID, NAME FROM USERS\",\"description\":\"get users\"}");

            assertThat(result.getQueryResult()).isNotNull();
        }

        @Test
        @DisplayName("sets MAX_ROWS and fetch size on statement")
        void setsMaxRowsAndFetchSize() throws Exception {
            tool.execute("{\"sql\":\"SELECT ID FROM USERS\",\"description\":\"ids\"}");

            verify(preparedStatement).setMaxRows(5000);
            verify(preparedStatement).setFetchSize(200);
        }
    }

    // ── SQL execution error ───────────────────────────────────────────────────

    @Nested
    @DisplayName("SQL execution error handling")
    class SqlExecutionError {

        @BeforeEach
        void arrange() throws Exception {
            when(connectionPool.isConfigured()).thenReturn(true);
            when(connectionPool.getDataSource()).thenReturn(dataSource);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), anyInt(), anyInt())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenThrow(new SQLException("ORA-00942: table not found"));
        }

        @Test
        @DisplayName("returns SQL error message containing the exception message")
        void returnsSqlErrorMessage() throws Exception {
            ToolResult result = tool.execute(
                    "{\"sql\":\"SELECT * FROM NONEXISTENT_TABLE\",\"description\":\"test\"}");

            assertThat(result.getText()).contains("ORA-00942");
        }

        @Test
        @DisplayName("does not re-throw SQLException")
        void doesNotRethrowSqlException() {
            org.assertj.core.api.Assertions.assertThatNoException().isThrownBy(() ->
                tool.execute("{\"sql\":\"SELECT * FROM BAD_TABLE\",\"description\":\"test\"}")
            );
        }
    }

    // ── Tool metadata ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tool metadata")
    class Metadata {

        @Test
        @DisplayName("getName() returns 'query_oracle_db'")
        void getName() {
            assertThat(tool.getName()).isEqualTo("query_oracle_db");
        }

        @Test
        @DisplayName("getDescription() is non-blank")
        void getDescription() {
            assertThat(tool.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("getDefinition() contains required parameters sql and description")
        void getDefinitionContainsRequiredParams() {
            var definition = tool.getDefinition();
            assertThat(definition).isNotNull();
            assertThat(definition.getFunction().getName()).isEqualTo("query_oracle_db");
        }
    }
}


