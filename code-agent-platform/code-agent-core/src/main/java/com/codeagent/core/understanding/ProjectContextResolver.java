package com.codeagent.core.understanding;

import com.codeagent.core.dto.CreateAgentTaskCommand;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProjectContextResolver {
    private final ProjectToolBindingService bindingService;

    public ProjectContextResolver(ProjectToolBindingService bindingService) {
        this.bindingService = bindingService;
    }

    public ProjectContext resolve(CreateAgentTaskCommand command, QueryUnderstandingResult understanding) {
        String projectKey = firstText(command.projectKey(), first(understanding == null ? List.of() : understanding.projectHints()));
        String serviceName = firstText(command.serviceName(), first(understanding == null ? List.of() : understanding.serviceHints()));
        Optional<ProjectToolBinding> maybeBinding = bindingService.find(projectKey, serviceName);
        if (maybeBinding.isEmpty()) {
            List<String> missing = new ArrayList<>();
            if (!hasText(projectKey) && !hasText(serviceName)) {
                missing.add("projectKeyOrServiceName");
            } else {
                missing.add("projectToolBinding");
            }
            return new ProjectContext(projectKey, serviceName, null, null, null, null,
                    null, null, null, null, null, null, null, List.of(),
                    false, false, missing, List.of());
        }

        ProjectToolBinding binding = maybeBinding.get();
        List<String> missingConfig = new ArrayList<>();
        require(missingConfig, "repoName", binding.repoName());
        require(missingConfig, "gitlabProjectId", binding.gitlabProjectId());
        require(missingConfig, "jenkinsJobName", binding.jenkinsJobName());
        require(missingConfig, "sonarqubeProjectKey", binding.sonarqubeProjectKey());
        require(missingConfig, "defaultBranch", binding.defaultBranch());
        require(missingConfig, "logIndex", binding.logIndex());
        require(missingConfig, "apmServiceName", binding.apmServiceName());

        return new ProjectContext(firstText(projectKey, binding.projectKey()),
                firstText(serviceName, binding.serviceName()),
                binding.repoName(),
                binding.gitlabProjectId(),
                binding.gitlabRepoUrl(),
                binding.jenkinsJobName(),
                binding.jenkinsPipelineName(),
                firstText(command.sonarqubeProjectKey(), binding.sonarqubeProjectKey()),
                firstText(command.branch(), understanding == null ? null : understanding.branch(), binding.defaultBranch()),
                binding.logIndex(),
                binding.apmServiceName(),
                binding.alertGroup(),
                binding.ownerTeam(),
                binding.environments(),
                true,
                missingConfig.isEmpty(),
                missingConfig,
                runtimeFacts(command, understanding));
    }

    private List<String> runtimeFacts(CreateAgentTaskCommand command, QueryUnderstandingResult understanding) {
        List<String> missing = new ArrayList<>();
        if (!hasText(command.commitSha()) && !hasText(understanding == null ? null : understanding.commitSha())) {
            missing.add("commitSha");
        }
        if (!hasText(understanding == null ? null : understanding.traceId())) {
            missing.add("traceId");
        }
        missing.add("buildNumber");
        missing.add("buildUrl");
        missing.add("mrIid");
        missing.add("failedStage");
        return missing.stream().distinct().toList();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
