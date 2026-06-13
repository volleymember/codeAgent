package com.codeagent.core.understanding;

import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectContextResolver {
    private final AgentProperties properties;

    public ProjectContextResolver(AgentProperties properties) {
        this.properties = properties;
    }

    public ProjectContext resolve(CreateAgentTaskCommand command, QueryUnderstandingResult understanding) {
        String projectKey = firstText(command.projectKey(), first(understanding == null ? List.of() : understanding.projectHints()));
        String serviceName = firstText(command.serviceName(), first(understanding == null ? List.of() : understanding.serviceHints()));
        AgentProperties.ProjectProperties configured = properties.getProjects().get(projectKey);
        if (configured == null && serviceName != null) {
            configured = properties.getProjects().get(serviceName);
        }
        List<String> missing = new ArrayList<>();
        String repoName = configured == null ? null : configured.getRepoName();
        String gitlabProjectId = configured == null ? null : configured.getGitlabProjectId();
        String jenkinsJobName = configured == null ? null : configured.getJenkinsJobName();
        String sonar = firstText(command.sonarqubeProjectKey(), configured == null ? null : configured.getSonarqubeProjectKey());
        String defaultBranch = firstText(command.branch(), understanding == null ? null : understanding.branch(),
                configured == null ? null : configured.getDefaultBranch());
        String logIndex = configured == null ? null : configured.getLogIndex();
        String apmServiceName = configured == null ? null : configured.getApmServiceName();

        require(missing, "repoName", repoName);
        require(missing, "gitlabProjectId", gitlabProjectId);
        require(missing, "jenkinsJobName", jenkinsJobName);
        require(missing, "sonarqubeProjectKey", sonar);
        require(missing, "defaultBranch", defaultBranch);
        require(missing, "logIndex", logIndex);
        require(missing, "apmServiceName", apmServiceName);

        return new ProjectContext(projectKey, serviceName, repoName, gitlabProjectId, jenkinsJobName,
                sonar, defaultBranch, logIndex, apmServiceName, missing.isEmpty(), missing);
    }

    private void require(List<String> missing, String field, String value) {
        if (value == null || value.isBlank()) {
            missing.add(field);
        }
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
