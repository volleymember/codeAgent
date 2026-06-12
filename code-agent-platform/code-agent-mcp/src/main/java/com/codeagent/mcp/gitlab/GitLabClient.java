package com.codeagent.mcp.gitlab;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class GitLabClient {
    private final IntegrationsProperties properties;

    public GitLabClient(IntegrationsProperties properties) {
        this.properties = properties;
    }

    public JsonNode getMergeRequest(GitLabMergeRequestRef ref) {
        return get("/api/v4/projects/%s/merge_requests/%s".formatted(project(ref.projectPath()), ref.mrIid()));
    }

    public JsonNode listCommits(GitLabMergeRequestRef ref) {
        return get("/api/v4/projects/%s/merge_requests/%s/commits".formatted(project(ref.projectPath()), ref.mrIid()));
    }

    public JsonNode getMergeRequestDiff(GitLabMergeRequestRef ref) {
        return get("/api/v4/projects/%s/merge_requests/%s/changes".formatted(project(ref.projectPath()), ref.mrIid()));
    }

    public JsonNode listReviewComments(GitLabMergeRequestRef ref) {
        return get("/api/v4/projects/%s/merge_requests/%s/discussions".formatted(project(ref.projectPath()), ref.mrIid()));
    }

    private JsonNode get(String uri) {
        IntegrationsProperties.GitLab gitlab = properties.getGitlab();
        if (!gitlab.configured()) {
            throw new BusinessException("GITLAB_NOT_CONFIGURED", "GITLAB_BASE_URL and GITLAB_TOKEN are required.");
        }
        return RestClient.builder()
                .baseUrl(gitlab.getBaseUrl())
                .defaultHeader("PRIVATE-TOKEN", gitlab.getToken())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build()
                .get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
    }

    private String project(String projectPath) {
        return URLEncoder.encode(projectPath, StandardCharsets.UTF_8);
    }
}
