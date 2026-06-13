package com.codeagent.core.understanding;

import java.util.List;
import java.util.Map;

public record ProjectContext(
        String projectKey,
        String serviceName,
        String repoName,
        String gitlabProjectId,
        String jenkinsJobName,
        String sonarqubeProjectKey,
        String defaultBranch,
        String logIndex,
        String apmServiceName,
        boolean complete,
        List<String> missingFields
) {
    public ProjectContext {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }

    public Map<String, Object> asKnownFacts() {
        Map<String, Object> facts = new java.util.LinkedHashMap<>();
        putIfPresent(facts, "projectKey", projectKey);
        putIfPresent(facts, "serviceName", serviceName);
        putIfPresent(facts, "repoName", repoName);
        putIfPresent(facts, "gitlabProjectId", gitlabProjectId);
        putIfPresent(facts, "jenkinsJobName", jenkinsJobName);
        putIfPresent(facts, "sonarqubeProjectKey", sonarqubeProjectKey);
        putIfPresent(facts, "defaultBranch", defaultBranch);
        putIfPresent(facts, "logIndex", logIndex);
        putIfPresent(facts, "apmServiceName", apmServiceName);
        return facts;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
