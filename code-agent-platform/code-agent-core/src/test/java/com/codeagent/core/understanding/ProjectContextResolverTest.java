package com.codeagent.core.understanding;

import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectContextResolverTest {
    @Test
    void resolvesProjectConfigFromProperties() {
        AgentProperties properties = new AgentProperties();
        AgentProperties.ProjectProperties project = new AgentProperties.ProjectProperties();
        project.setRepoName("payment-service");
        project.setGitlabProjectId("123");
        project.setJenkinsJobName("payment-ci");
        project.setSonarqubeProjectKey("payment-service");
        project.setDefaultBranch("main");
        project.setLogIndex("logs-payment");
        project.setApmServiceName("payment-api");
        properties.setProjects(Map.of("payment", project));

        ProjectContext context = new ProjectContextResolver(properties).resolve(
                new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment", null, null,
                        null, null, null, null), query());

        assertThat(context.jenkinsJobName()).isEqualTo("payment-ci");
        assertThat(context.gitlabProjectId()).isEqualTo("123");
        assertThat(context.sonarqubeProjectKey()).isEqualTo("payment-service");
        assertThat(context.complete()).isTrue();
    }

    @Test
    void reportsMissingFieldsWhenProjectCannotBeResolved() {
        ProjectContext context = new ProjectContextResolver(new AgentProperties()).resolve(
                new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "unknown", null, null,
                        null, null, null, null), query());

        assertThat(context.complete()).isFalse();
        assertThat(context.missingFields()).contains("jenkinsJobName", "gitlabProjectId", "sonarqubeProjectKey");
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("q", "q", List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", "", "", List.of(), 0.2);
    }
}
