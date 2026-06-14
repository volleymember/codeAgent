package com.codeagent.core.understanding;

import java.util.List;
import java.util.Map;

public record ProjectToolBinding(
        String projectKey,
        String serviceName,
        String repoName,
        String gitlabProjectId,
        String gitlabRepoUrl,
        String defaultBranch,
        String jenkinsJobName,
        String jenkinsPipelineName,
        String sonarqubeProjectKey,
        String logIndex,
        String apmServiceName,
        String alertGroup,
        String ownerTeam,
        List<String> environments,
        boolean enabled
) {
    public ProjectToolBinding {
        environments = environments == null ? List.of() : List.copyOf(environments);
    }

    public Map<String, Object> asFacts() {
        Map<String, Object> facts = new java.util.LinkedHashMap<>();
        put(facts, "projectKey", projectKey);
        put(facts, "serviceName", serviceName);
        put(facts, "repoName", repoName);
        put(facts, "gitlabProjectId", gitlabProjectId);
        put(facts, "gitlabRepoUrl", gitlabRepoUrl);
        put(facts, "defaultBranch", defaultBranch);
        put(facts, "jenkinsJobName", jenkinsJobName);
        put(facts, "jenkinsPipelineName", jenkinsPipelineName);
        put(facts, "sonarqubeProjectKey", sonarqubeProjectKey);
        put(facts, "logIndex", logIndex);
        put(facts, "apmServiceName", apmServiceName);
        put(facts, "alertGroup", alertGroup);
        put(facts, "ownerTeam", ownerTeam);
        if (!environments.isEmpty()) {
            facts.put("environments", environments);
        }
        facts.put("bindingEnabled", enabled);
        return facts;
    }

    private static void put(Map<String, Object> facts, String key, String value) {
        if (value != null && !value.isBlank()) {
            facts.put(key, value);
        }
    }
}
