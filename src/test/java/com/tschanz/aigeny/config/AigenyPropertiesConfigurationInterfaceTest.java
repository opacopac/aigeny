package com.tschanz.aigeny.config;

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
    @DisplayName("DbConfiguration")
    class DbConfigurationContract {

        @Test
        @DisplayName("AigenyProperties.Db is assignable to DbConfiguration")
        void dbIsAssignableToInterface() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            assertThat(db).isInstanceOf(DbConfiguration.class);
        }

        @Test
        @DisplayName("getUrl() delegates to the stored value")
        void getUrlDelegates() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUrl("jdbc:oracle:thin:@host:1521/XE");
            DbConfiguration config = db;
            assertThat(config.getUrl()).isEqualTo("jdbc:oracle:thin:@host:1521/XE");
        }

        @Test
        @DisplayName("getUsername() delegates to the stored value")
        void getUsernameDelegates() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUsername("myuser");
            DbConfiguration config = db;
            assertThat(config.getUsername()).isEqualTo("myuser");
        }

        @Test
        @DisplayName("getPassword() delegates to the stored value")
        void getPasswordDelegates() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setPassword("secret");
            DbConfiguration config = db;
            assertThat(config.getPassword()).isEqualTo("secret");
        }

        @Test
        @DisplayName("getEffectiveSchema() returns schema when set")
        void getEffectiveSchemaReturnsSchemaWhenSet() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUsername("READONLY");
            db.setSchema("DATA_SCHEMA");
            DbConfiguration config = db;
            assertThat(config.getEffectiveSchema()).isEqualTo("DATA_SCHEMA");
        }

        @Test
        @DisplayName("getEffectiveSchema() falls back to username when schema is blank")
        void getEffectiveSchemaFallsBackToUsername() {
            AigenyProperties.Db db = new AigenyProperties.Db();
            db.setUsername("MYUSER");
            db.setSchema("");
            DbConfiguration config = db;
            assertThat(config.getEffectiveSchema()).isEqualTo("MYUSER");
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
