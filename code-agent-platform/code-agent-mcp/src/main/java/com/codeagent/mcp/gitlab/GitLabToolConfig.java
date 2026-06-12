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
    ToolExecutor gitlabGetMergeRequestTool(GitLabClient client,
                                           RawOutputStore rawOutputStore,
                                           DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.get_merge_request", "Fetch GitLab merge request metadata.",
                List.of("gitlabMrUrl"), List.of("git", "gitlab", "mr", "merge", "metadata", "risk"), 700, false),
                rawOutputStore, dataSandboxService, request -> {
            GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(request.stringInput("gitlabMrUrl"));
            JsonNode raw = client.getMergeRequest(ref);
            String title = raw.path("title").asText("GitLab merge request");
            String summary = "MR !%s `%s`, state=%s, target=%s, source=%s".formatted(
                    ref.mrIid(), title, raw.path("state").asText("unknown"),
                    raw.path("target_branch").asText("unknown"), raw.path("source_branch").asText("unknown"));
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_merge_request", title, summary, 0.86, ref.sourceUri(), null,
                    Map.of("projectPath", ref.projectPath(), "mrIid", ref.mrIid()))));
        });
    }

    @Bean
    ToolExecutor gitlabListCommitsTool(GitLabClient client,
                                       RawOutputStore rawOutputStore,
                                       DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.list_commits", "Fetch GitLab MR commits.",
                List.of("gitlabMrUrl"), List.of("git", "commit", "change", "mr"), 900, false),
                rawOutputStore, dataSandboxService, request -> {
            GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(request.stringInput("gitlabMrUrl"));
            JsonNode raw = client.listCommits(ref);
            int count = raw.isArray() ? raw.size() : 0;
            String latest = count > 0 ? raw.path(0).path("title").asText("") : "";
            String summary = "MR !%s has %d commits. Latest commit: %s".formatted(ref.mrIid(), count, latest);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_mr_commits", "MR commits", summary, 0.78, ref.sourceUri(), null,
                    Map.of("projectPath", ref.projectPath(), "commitCount", count))));
        });
    }

    @Bean
    ToolExecutor gitlabGetMergeRequestDiffTool(GitLabClient client,
                                               RawOutputStore rawOutputStore,
                                               DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.get_merge_request_diff", "Fetch GitLab MR diff metadata.",
                List.of("gitlabMrUrl"), List.of("git", "diff", "code", "change", "mr", "risk"), 2400, true),
                rawOutputStore, dataSandboxService, request -> {
            GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(request.stringInput("gitlabMrUrl"));
            JsonNode raw = client.getMergeRequestDiff(ref);
            JsonNode changes = raw.path("changes");
            int count = changes.isArray() ? changes.size() : 0;
            String firstFile = count > 0 ? changes.path(0).path("new_path").asText(changes.path(0).path("old_path").asText()) : "";
            String summary = "MR !%s changes %d files. First changed file: %s".formatted(ref.mrIid(), count, firstFile);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_mr_diff", "MR file changes", summary, 0.88, ref.sourceUri(), null,
                    Map.of("projectPath", ref.projectPath(), "changedFiles", count))));
        });
    }

    @Bean
    ToolExecutor gitlabListReviewCommentsTool(GitLabClient client,
                                              RawOutputStore rawOutputStore,
                                              DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("gitlab.list_review_comments", "Fetch GitLab MR review discussions.",
                List.of("gitlabMrUrl"), List.of("git", "review", "comment", "mr"), 1100, false),
                rawOutputStore, dataSandboxService, request -> {
            GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(request.stringInput("gitlabMrUrl"));
            JsonNode raw = client.listReviewComments(ref);
            int count = raw.isArray() ? raw.size() : 0;
            String summary = "MR !%s has %d review discussions.".formatted(ref.mrIid(), count);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "gitlab_review_comments", "MR review comments", summary, 0.72, ref.sourceUri(), null,
                    Map.of("projectPath", ref.projectPath(), "discussionCount", count))));
        });
    }

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost) {
        return new ToolDefinition(name, "GitLab", description, requiredInputs, 15000,
                tags, estimatedOutputTokens, highCost);
    }
}
