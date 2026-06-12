package com.codeagent.core.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentTaskCommand(
        @NotBlank String taskType,
        @NotBlank String projectKey,
        String gitlabMrUrl,
        String jenkinsBuildUrl,
        String sonarqubeProjectKey,
        String jiraIssueKey,
        String confluencePageUrl,
        String openApiUrl
) {
}
