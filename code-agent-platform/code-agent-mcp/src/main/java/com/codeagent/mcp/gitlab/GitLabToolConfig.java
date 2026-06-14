package com.codeagent.mcp.gitlab;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.sandbox.DataSandboxService;
import com.codeagent.mcp.tool.JsonToolExecutor;
import com.codeagent.mcp.tool.ToolExecutionPayload;
import com.codeagent.mcp.tool.ToolExecutor;
import com.codeagent.storage.raw.RawOutputStore;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class GitLabToolConfig {
    @Bean
    ToolExecutor gitlabFindMergeRequestByCommitTool(GitLabClient client,
                                                    RawOutputStore rawOutputStore,
                                                    DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.find_merge_request_by_commit", "Find GitLab merge request by commit.",
                List.of("gitlabProjectId", "commitSha"), List.of("mrIid", "sourceBranch", "targetBranch", "changedFiles"),
                List.of("git", "gitlab", "mr", "commit", "discovery"), 700, false, "discovery"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String commitSha = request.stringInput("commitSha");
            JsonNode raw = client.findMergeRequestsByCommit(projectId, commitSha);
            JsonNode mr = first(raw);
            String mrIid = mr.path("iid").asText("");
            String sourceBranch = mr.path("source_branch").asText("");
            String targetBranch = mr.path("target_branch").asText("");
            String summary = "GitLab project `%s` commit `%s` matched MR !%s source=%s target=%s".formatted(
                    projectId, commitSha, empty(mrIid), empty(sourceBranch), empty(targetBranch));
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("gitlabProjectId", projectId);
            metadata.put("commitSha", commitSha);
            put(metadata, "mrIid", mrIid);
            put(metadata, "sourceBranch", sourceBranch);
            put(metadata, "targetBranch", targetBranch);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_mr_discovery", "GitLab MR by commit", summary, 0.82,
                    sourceUri(projectId, mrIid), null, metadata)));
        });
    }

    @Bean
    ToolExecutor gitlabFindRecentMergeRequestsTool(GitLabClient client,
                                                   RawOutputStore rawOutputStore,
                                                   DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.find_recent_merge_requests", "Find recent GitLab merge requests.",
                List.of("gitlabProjectId", "timeRange"), List.of("mrIid", "commitSha", "sourceBranch", "targetBranch"),
                List.of("git", "gitlab", "mr", "recent", "discovery", "time"), 900, false, "discovery"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String startTime = startTime(request.input().get("timeRange"));
            JsonNode raw = client.findRecentMergeRequests(projectId, startTime);
            JsonNode mr = first(raw);
            String mrIid = mr.path("iid").asText("");
            String commitSha = mr.path("sha").asText("");
            String sourceBranch = mr.path("source_branch").asText("");
            String targetBranch = mr.path("target_branch").asText("");
            String summary = "GitLab project `%s` recent MR !%s commit=%s source=%s target=%s".formatted(
                    projectId, empty(mrIid), empty(commitSha), empty(sourceBranch), empty(targetBranch));
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("gitlabProjectId", projectId);
            put(metadata, "mrIid", mrIid);
            put(metadata, "commitSha", commitSha);
            put(metadata, "sourceBranch", sourceBranch);
            put(metadata, "targetBranch", targetBranch);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_recent_mr_discovery", "GitLab recent merge requests", summary, 0.74,
                    sourceUri(projectId, mrIid), null, metadata)));
        });
    }

    @Bean
    ToolExecutor gitlabGetMergeRequestTool(GitLabClient client,
                                           RawOutputStore rawOutputStore,
                                           DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.get_merge_request", "Fetch GitLab merge request metadata.",
                List.of("gitlabProjectId", "mrIid"), List.of("mrIid", "sourceBranch", "targetBranch", "commitSha"),
                List.of("git", "gitlab", "mr", "merge", "metadata", "risk"), 700, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String mrIid = request.stringInput("mrIid");
            JsonNode raw = client.getMergeRequest(projectId, mrIid);
            String title = raw.path("title").asText("GitLab merge request");
            String summary = "MR !%s `%s`, state=%s, target=%s, source=%s".formatted(
                    mrIid, title, raw.path("state").asText("unknown"),
                    raw.path("target_branch").asText("unknown"), raw.path("source_branch").asText("unknown"));
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_merge_request", title, summary, 0.86, sourceUri(projectId, mrIid), null,
                    Map.of("gitlabProjectId", projectId, "mrIid", mrIid,
                            "sourceBranch", raw.path("source_branch").asText(""),
                            "targetBranch", raw.path("target_branch").asText(""),
                            "commitSha", raw.path("sha").asText("")))));
        });
    }

    @Bean
    ToolExecutor gitlabListCommitsTool(GitLabClient client,
                                       RawOutputStore rawOutputStore,
                                       DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.list_commits", "Fetch GitLab MR commits.",
                List.of("gitlabProjectId", "mrIid"), List.of("commitSha"),
                List.of("git", "commit", "change", "mr"), 900, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String mrIid = request.stringInput("mrIid");
            JsonNode raw = client.listCommits(projectId, mrIid);
            int count = raw.isArray() ? raw.size() : 0;
            String latest = count > 0 ? raw.path(0).path("title").asText("") : "";
            String commitSha = count > 0 ? raw.path(0).path("id").asText("") : "";
            String summary = "MR !%s has %d commits. Latest commit: %s".formatted(mrIid, count, latest);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_mr_commits", "MR commits", summary, 0.78, sourceUri(projectId, mrIid), null,
                    Map.of("gitlabProjectId", projectId, "mrIid", mrIid, "commitCount", count, "commitSha", commitSha))));
        });
    }

    @Bean
    ToolExecutor gitlabGetMergeRequestDiffTool(GitLabClient client,
                                               RawOutputStore rawOutputStore,
                                               DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.get_merge_request_diff", "Fetch GitLab MR diff metadata.",
                List.of("gitlabProjectId", "mrIid"), List.of("changedFiles", "filePath"),
                List.of("git", "diff", "code", "change", "mr", "risk"), 2400, true, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String mrIid = request.stringInput("mrIid");
            JsonNode raw = client.getMergeRequestDiff(projectId, mrIid);
            JsonNode changes = raw.path("changes");
            int count = changes.isArray() ? changes.size() : 0;
            String firstFile = count > 0 ? changes.path(0).path("new_path").asText(changes.path(0).path("old_path").asText()) : "";
            String summary = "MR !%s changes %d files. First changed file: %s".formatted(mrIid, count, firstFile);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_mr_diff", "MR file changes", summary, 0.88, sourceUri(projectId, mrIid), null,
                    Map.of("gitlabProjectId", projectId, "mrIid", mrIid, "changedFiles", count, "filePath", firstFile))));
        });
    }

    @Bean
    ToolExecutor gitlabListReviewCommentsTool(GitLabClient client,
                                              RawOutputStore rawOutputStore,
                                              DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.list_review_comments", "Fetch GitLab MR review discussions.",
                List.of("gitlabProjectId", "mrIid"), List.of("reviewComments"),
                List.of("git", "review", "comment", "mr"), 1100, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            String projectId = request.stringInput("gitlabProjectId");
            String mrIid = request.stringInput("mrIid");
            JsonNode raw = client.listReviewComments(projectId, mrIid);
            int count = raw.isArray() ? raw.size() : 0;
            String summary = "MR !%s has %d review discussions.".formatted(mrIid, count);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_review_comments", "MR review comments", summary, 0.72, sourceUri(projectId, mrIid), null,
                    Map.of("gitlabProjectId", projectId, "mrIid", mrIid, "discussionCount", count))));
        });
    }

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> outputFacts,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost,
                               String toolType) {
        return new ToolDefinition(name, "GitLab", description, requiredInputs, 15000,
                tags, estimatedOutputTokens, highCost, List.of(), outputFacts, toolType, List.of(), highCost ? 8 : 3);
    }

    private JsonNode first(JsonNode raw) {
        return raw.isArray() && raw.size() > 0 ? raw.path(0) : raw;
    }

    private String sourceUri(String projectId, String mrIid) {
        return mrIid == null || mrIid.isBlank()
                ? "gitlab://project/" + projectId + "/merge_requests"
                : "gitlab://project/" + projectId + "/merge_requests/" + mrIid;
    }

    private String startTime(Object timeRange) {
        if (timeRange instanceof Map<?, ?> map && map.get("startTime") != null) {
            return String.valueOf(map.get("startTime"));
        }
        return "";
    }

    private void put(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private String empty(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
