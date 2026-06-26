package com.tschanz.aigeny.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ConfigBeans} correctly delegates to {@link AigenyProperties}
 * sub-objects and exposes them as the corresponding configuration interfaces.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConfigBeans")
class ConfigBeansTest {

    private ConfigBeans configBeans;

    @Mock
    private AigenyProperties props;

    private AigenyProperties.Llm        llm        = new AigenyProperties.Llm();
    private AigenyProperties.Db         db         = new AigenyProperties.Db();
    private AigenyProperties.Jira       jira       = new AigenyProperties.Jira();
    private AigenyProperties.Bitbucket  bitbucket  = new AigenyProperties.Bitbucket();

    @BeforeEach
    void setUp() {
        configBeans = new ConfigBeans();
        lenient().when(props.getLlm()).thenReturn(llm);
        lenient().when(props.getDb()).thenReturn(db);
        lenient().when(props.getJira()).thenReturn(jira);
        lenient().when(props.getBitbucket()).thenReturn(bitbucket);
    }

    @Test
    @DisplayName("llmConfiguration bean returns the Llm sub-object as LlmConfiguration")
    void llmConfigurationBeanReturnsLlmSubObject() {
        LlmConfiguration result = configBeans.llmConfiguration(props);
        assertThat(result).isSameAs(llm);
        assertThat(result).isInstanceOf(LlmConfiguration.class);
    }

    @Test
    @DisplayName("dbConfiguration bean returns the Db sub-object as DbConfiguration")
    void dbConfigurationBeanReturnsDbSubObject() {
        DbConfiguration result = configBeans.dbConfiguration(props);
        assertThat(result).isSameAs(db);
        assertThat(result).isInstanceOf(DbConfiguration.class);
    }

    @Test
    @DisplayName("jiraConfiguration bean returns the Jira sub-object as JiraConfiguration")
    void jiraConfigurationBeanReturnsJiraSubObject() {
        JiraConfiguration result = configBeans.jiraConfiguration(props);
        assertThat(result).isSameAs(jira);
        assertThat(result).isInstanceOf(JiraConfiguration.class);
    }

    @Test
    @DisplayName("bitbucketConfiguration bean returns the Bitbucket sub-object as BitbucketConfiguration")
    void bitbucketConfigurationBeanReturnsBitbucketSubObject() {
        BitbucketConfiguration result = configBeans.bitbucketConfiguration(props);
        assertThat(result).isSameAs(bitbucket);
        assertThat(result).isInstanceOf(BitbucketConfiguration.class);
    }

    @Test
    @DisplayName("beans reflect live property values (not snapshots)")
    void beansReflectLivePropertyValues() {
        // Given – llm bean is obtained once
        LlmConfiguration llmConfig = configBeans.llmConfiguration(props);
        llm.setProvider("claude");

        // The returned object is the same mutable instance, so changes are visible
        assertThat(llmConfig.getProvider()).isEqualTo("claude");
    }
}
