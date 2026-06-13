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
        String openApiUrl,
        String query,
        String serviceName,
        String startTime,
        String endTime,
        String branch,
        String commitSha
) {
    public CreateAgentTaskCommand(String taskType,
                                  String projectKey,
                                  String gitlabMrUrl,
                                  String jenkinsBuildUrl,
                                  String sonarqubeProjectKey,
                                  String jiraIssueKey,
                                  String confluencePageUrl,
                                  String openApiUrl) {
        this(taskType, projectKey, gitlabMrUrl, jenkinsBuildUrl, sonarqubeProjectKey, jiraIssueKey,
                confluencePageUrl, openApiUrl, null, null, null, null, null, null);
    }
}
