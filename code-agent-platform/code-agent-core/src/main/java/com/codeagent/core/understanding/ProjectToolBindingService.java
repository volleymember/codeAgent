package com.codeagent.core.understanding;

import com.codeagent.core.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class ProjectToolBindingService {
    private final AgentProperties properties;

    public ProjectToolBindingService(AgentProperties properties) {
        this.properties = properties;
    }

    public Optional<ProjectToolBinding> find(String projectKey, String serviceName) {
        ProjectToolBinding byProject = binding(projectKey);
        if (byProject != null && byProject.enabled()) {
            return Optional.of(byProject);
        }
        ProjectToolBinding byServiceKey = binding(serviceName);
        if (byServiceKey != null && byServiceKey.enabled()) {
            return Optional.of(byServiceKey);
        }
        return properties.getProjects().entrySet().stream()
                .map(entry -> toBinding(entry.getKey(), entry.getValue()))
                .filter(ProjectToolBinding::enabled)
                .filter(binding -> equalsText(projectKey, binding.projectKey())
                        || equalsText(serviceName, binding.serviceName())
                        || equalsText(serviceName, binding.apmServiceName())
                        || equalsText(projectKey, binding.repoName()))
                .findFirst();
    }

    private ProjectToolBinding binding(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        AgentProperties.ProjectProperties configured = properties.getProjects().get(key);
        return configured == null ? null : toBinding(key, configured);
    }

    private ProjectToolBinding toBinding(String mapKey, AgentProperties.ProjectProperties configured) {
        String projectKey = firstText(configured.getProjectKey(), mapKey);
        String serviceName = firstText(configured.getServiceName(), configured.getApmServiceName(), configured.getRepoName());
        return new ProjectToolBinding(projectKey, serviceName, configured.getRepoName(),
                configured.getGitlabProjectId(), configured.getGitlabRepoUrl(), configured.getDefaultBranch(),
                configured.getJenkinsJobName(), configured.getJenkinsPipelineName(), configured.getSonarqubeProjectKey(),
                configured.getLogIndex(), configured.getApmServiceName(), configured.getAlertGroup(),
                configured.getOwnerTeam(), configured.getEnvironments(), configured.isEnabled());
    }

    private boolean equalsText(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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
