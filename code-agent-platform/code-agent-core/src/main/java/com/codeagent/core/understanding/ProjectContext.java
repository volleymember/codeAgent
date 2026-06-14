package com.codeagent.core.understanding;

import java.util.List;
import java.util.Map;

public record ProjectContext(
        String projectKey,
        String serviceName,
        String repoName,
        String gitlabProjectId,
        String gitlabRepoUrl,
        String jenkinsJobName,
        String jenkinsPipelineName,
        String sonarqubeProjectKey,
        String defaultBranch,
        String logIndex,
        String apmServiceName,
        String alertGroup,
        String ownerTeam,
        List<String> environments,
        boolean bindingFound,
        boolean complete,
        List<String> missingConfigFields,
        List<String> missingRuntimeFacts
) {
    public ProjectContext {
        environments = environments == null ? List.of() : List.copyOf(environments);
        missingConfigFields = missingConfigFields == null ? List.of() : List.copyOf(missingConfigFields);
        missingRuntimeFacts = missingRuntimeFacts == null ? List.of() : List.copyOf(missingRuntimeFacts);
    }

    public ProjectContext(String projectKey,
                          String serviceName,
                          String repoName,
                          String gitlabProjectId,
                          String jenkinsJobName,
                          String sonarqubeProjectKey,
                          String defaultBranch,
                          String logIndex,
                          String apmServiceName,
                          boolean complete,
                          List<String> missingFields) {
        this(projectKey, serviceName, repoName, gitlabProjectId, null, jenkinsJobName, null,
                sonarqubeProjectKey, defaultBranch, logIndex, apmServiceName, null, null, List.of(),
                complete, complete, missingFields, List.of());
    }

    public List<String> missingFields() {
        return missingConfigFields;
    }

    public Map<String, Object> asKnownFacts() {
        Map<String, Object> facts = new java.util.LinkedHashMap<>();
        putIfPresent(facts, "projectKey", projectKey);
        putIfPresent(facts, "serviceName", serviceName);
        putIfPresent(facts, "repoName", repoName);
        putIfPresent(facts, "gitlabProjectId", gitlabProjectId);
        putIfPresent(facts, "gitlabRepoUrl", gitlabRepoUrl);
        putIfPresent(facts, "jenkinsJobName", jenkinsJobName);
        putIfPresent(facts, "jenkinsPipelineName", jenkinsPipelineName);
        putIfPresent(facts, "sonarqubeProjectKey", sonarqubeProjectKey);
        putIfPresent(facts, "defaultBranch", defaultBranch);
        putIfPresent(facts, "logIndex", logIndex);
        putIfPresent(facts, "apmServiceName", apmServiceName);
        putIfPresent(facts, "alertGroup", alertGroup);
        putIfPresent(facts, "ownerTeam", ownerTeam);
        if (!environments.isEmpty()) {
            facts.put("environments", environments);
        }
        return facts;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
