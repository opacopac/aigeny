package com.tschanz.aigeny.config;
import com.tschanz.aigeny.bitbucket.BitbucketConfiguration;
import com.tschanz.aigeny.database.DbMcpConfiguration;
import com.tschanz.aigeny.database.DbServerConfiguration;
import com.tschanz.aigeny.jira.JiraConfiguration;
import com.tschanz.aigeny.llm.LlmConfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every AigenyProperties nested class correctly implements
 * its corresponding configuration interface (DIP – point 7).
 */
@DisplayName("AigenyProperties configuration interfaces")
class AigenyPropertiesConfigurationInterfaceTest {

    @Nested
    @DisplayName("LlmConfiguration")
    class LlmConfigurationContract {

        @Test
        @DisplayName("AigenyProperties.Llm is assignable to LlmConfiguration")
        void llmIsAssignableToInterface() {
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            assertThat(llm).isInstanceOf(LlmConfiguration.class);
        }

        @Test
        @DisplayName("getProvider() delegates to the stored value")
        void getProviderDelegates() {
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setProvider("claude");
            LlmConfiguration config = llm;
            assertThat(config.getProvider()).isEqualTo("claude");
        }

        @Test
        @DisplayName("getApiKey() delegates to the stored value")
        void getApiKeyDelegates() {
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setApiKey("sk-test-key");
            LlmConfiguration config = llm;
            assertThat(config.getApiKey()).isEqualTo("sk-test-key");
        }

        @Test
        @DisplayName("getBaseUrl() delegates to the stored value")
        void getBaseUrlDelegates() {
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setBaseUrl("https://api.anthropic.com/v1");
            LlmConfiguration config = llm;
            assertThat(config.getBaseUrl()).isEqualTo("https://api.anthropic.com/v1");
        }

        @Test
        @DisplayName("getModel() delegates to the stored value")
        void getModelDelegates() {
            AigenyProperties.Llm llm = new AigenyProperties.Llm();
            llm.setModel("claude-opus-4-5");
            LlmConfiguration config = llm;
            assertThat(config.getModel()).isEqualTo("claude-opus-4-5");
        }
    }

    @Nested
    @DisplayName("DbServerConfiguration / DbMcpConfiguration")
    class DbConfigurationContract {

        @Test
        @DisplayName("AigenyProperties.Db is assignable to DbServerConfiguration and DbMcpConfiguration")
        void dbIsAssignableToInterface() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            assertThat(db).isInstanceOf(DbServerConfiguration.class);
            assertThat(db).isInstanceOf(DbMcpConfiguration.class);
        }

        @Test
        @DisplayName("getStages() is empty by default")
        void getStagesDefaultsToEmpty() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            DbServerConfiguration config = db;
            assertThat(config.getStages()).isEmpty();
        }

        @Test
        @DisplayName("getStage() looks up a configured stage case-insensitively")
        void getStageLooksUpCaseInsensitively() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            AigenyProperties.Db.StageProps stage = new AigenyProperties.Db.StageProps();
            stage.setUrl("jdbc:oracle:thin:@host:1521/XE");
            db.getStages().put("INTE", stage);
            DbServerConfiguration config = db;
            assertThat(config.getStage("inte")).isSameAs(stage);
            assertThat(config.getStage("PROD")).isNull();
        }

        @Test
        @DisplayName("Stage.getUrl()/getUsername()/getPassword() delegate to the stored values")
        void stageFieldsDelegate() {
            AigenyProperties.Db.StageProps stage = new AigenyProperties.Db.StageProps();
            stage.setUrl("jdbc:oracle:thin:@host:1521/XE");
            stage.setUsername("myuser");
            stage.setPassword("secret");
            assertThat(stage.getUrl()).isEqualTo("jdbc:oracle:thin:@host:1521/XE");
            assertThat(stage.getUsername()).isEqualTo("myuser");
            assertThat(stage.getPassword()).isEqualTo("secret");
        }

        @Test
        @DisplayName("Stage.getEffectiveSchema() returns schema when set")
        void getEffectiveSchemaReturnsSchemaWhenSet() {
            AigenyProperties.Db.StageProps stage = new AigenyProperties.Db.StageProps();
            stage.setUsername("READONLY");
            stage.setSchema("DATA_SCHEMA");
            assertThat(stage.getEffectiveSchema()).isEqualTo("DATA_SCHEMA");
        }

        @Test
        @DisplayName("Stage.getEffectiveSchema() falls back to username when schema is blank")
        void getEffectiveSchemaFallsBackToUsername() {
            AigenyProperties.Db.StageProps stage = new AigenyProperties.Db.StageProps();
            stage.setUsername("MYUSER");
            stage.setSchema("");
            assertThat(stage.getEffectiveSchema()).isEqualTo("MYUSER");
        }

        @Test
        @DisplayName("getDefaultContext()/getDefaultStage() default to pflege/INTE")
        void defaultContextAndStageHaveSensibleDefaults() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            DbMcpConfiguration config = db;
            assertThat(config.getDefaultContext()).isEqualTo("pflege");
            assertThat(config.getDefaultStage()).isEqualTo("INTE");
        }

        @Test
        @DisplayName("getPassword()/setPassword() convenience methods operate on the default stage")
        void passwordConvenienceDelegatesToDefaultStage() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setPassword("secret");
            assertThat(db.getPassword()).isEqualTo("secret");
            assertThat(db.getStage(db.getDefaultStage()).getPassword()).isEqualTo("secret");
        }

        @Test
        @DisplayName("getMcpServerUrl() defaults to blank")
        void getMcpServerUrlDefaultsToBlank() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            DbMcpConfiguration config = db;
            assertThat(config.getMcpServerUrl()).isBlank();
        }

        @Test
        @DisplayName("getMcpServerUrl() delegates to the stored value")
        void getMcpServerUrlDelegates() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setMcpServerUrl("http://mcp-host:8081");
            DbMcpConfiguration config = db;
            assertThat(config.getMcpServerUrl()).isEqualTo("http://mcp-host:8081");
        }

        @Test
        @DisplayName("getMcpServerHeaders() defaults to an empty map")
        void getMcpServerHeadersDefaultsToEmpty() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            DbMcpConfiguration config = db;
            assertThat(config.getMcpServerHeaders()).isEmpty();
        }

        @Test
        @DisplayName("getMcpServerHeaders() delegates to the stored value")
        void getMcpServerHeadersDelegates() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setMcpServerHeaders(java.util.Map.of("X-API-Key", "secret-value"));
            DbMcpConfiguration config = db;
            assertThat(config.getMcpServerHeaders()).containsEntry("X-API-Key", "secret-value");
        }

        @Test
        @DisplayName("getMcpServerHeaders() never returns null, even when explicitly set to null")
        void getMcpServerHeadersNeverNull() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setMcpServerHeaders(null);
            DbMcpConfiguration config = db;
            assertThat(config.getMcpServerHeaders()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("JiraConfiguration")
    class JiraConfigurationContract {

        @Test
        @DisplayName("AigenyProperties.Jira is assignable to JiraConfiguration")
        void jiraIsAssignableToInterface() {
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            assertThat(jira).isInstanceOf(JiraConfiguration.class);
        }

        @Test
        @DisplayName("getBaseUrl() delegates to the stored value")
        void getBaseUrlDelegates() {
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setBaseUrl("https://jira.example.com");
            JiraConfiguration config = jira;
            assertThat(config.getBaseUrl()).isEqualTo("https://jira.example.com");
        }

        @Test
        @DisplayName("getToken() delegates to the stored value")
        void getTokenDelegates() {
            AigenyProperties.Jira jira = new AigenyProperties.Jira();
            jira.setToken("jira-pat-token");
            JiraConfiguration config = jira;
            assertThat(config.getToken()).isEqualTo("jira-pat-token");
        }
    }

    @Nested
    @DisplayName("BitbucketConfiguration")
    class BitbucketConfigurationContract {

        @Test
        @DisplayName("AigenyProperties.Bitbucket is assignable to BitbucketConfiguration")
        void bitbucketIsAssignableToInterface() {
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            assertThat(bitbucket).isInstanceOf(BitbucketConfiguration.class);
        }

        @Test
        @DisplayName("getBaseUrl() delegates to the stored value")
        void getBaseUrlDelegates() {
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setBaseUrl("https://code.example.com");
            BitbucketConfiguration config = bitbucket;
            assertThat(config.getBaseUrl()).isEqualTo("https://code.example.com");
        }

        @Test
        @DisplayName("getToken() delegates to the stored value")
        void getTokenDelegates() {
            AigenyProperties.Bitbucket bitbucket = new AigenyProperties.Bitbucket();
            bitbucket.setToken("bb-pat-token");
            BitbucketConfiguration config = bitbucket;
            assertThat(config.getToken()).isEqualTo("bb-pat-token");
        }
    }
}
