package com.codeagent.rag.collector;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.mcp.gitlab.GitLabClient;
import com.codeagent.mcp.gitlab.GitLabMergeRequestRef;
import com.codeagent.mcp.gitlab.GitLabUrlParser;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.SourceSystem;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GitLabEvidenceCollector extends CollectorSupport implements EvidenceCollector {
    private final GitLabClient gitLabClient;

    public GitLabEvidenceCollector(GitLabClient gitLabClient) {
        this.gitLabClient = gitLabClient;
    }

    @Override
    public SourceSystem sourceSystem() {
        return SourceSystem.GITLAB;
    }

    @Override
    public Evidence collect(IndexEvidenceRequest request) {
        requireText(request.getGitlabMrUrl(), "GITLAB_MR_URL_REQUIRED", "gitlabMrUrl is required for GitLab evidence indexing.");
        GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(request.getGitlabMrUrl());
        EvidenceType evidenceType = request.getEvidenceType();
        JsonNode raw = switch (evidenceType) {
            case GITLAB_MERGE_REQUEST -> gitLabClient.getMergeRequest(ref);
            case GITLAB_COMMITS -> gitLabClient.listCommits(ref);
            case GITLAB_DIFF -> gitLabClient.getMergeRequestDiff(ref);
            case GITLAB_REVIEW_COMMENTS -> gitLabClient.listReviewComments(ref);
            default -> throw new com.codeagent.common.exception.BusinessException(
                    "GITLAB_EVIDENCE_TYPE_UNSUPPORTED", "Unsupported GitLab evidence type: " + evidenceType);
        };
        Map<String, Object> metadata = metadata(request);
        put(metadata, "projectPath", ref.projectPath());
        put(metadata, "mrIid", ref.mrIid());
        enrichGitLabMetadata(evidenceType, raw, metadata);
        String title = title(evidenceType, raw);
        String summary = summary(evidenceType, raw, ref);
        String filePath = fallback(request.getFilePath(), firstChangedFile(evidenceType, raw, ref.projectPath()));
        log.info("Collected GitLab evidence type={} projectKey={} source={}", evidenceType, request.getProjectKey(), ref.sourceUri());
        return Evidence.builder()
                .evidenceId(nextEvidenceId())
                .taskNo(request.getTaskNo())
                .projectKey(request.getProjectKey())
                .branch(fallback(request.getBranch(), raw.path("source_branch").asText(null)))
                .commitId(fallback(request.getCommitId(), latestCommitId(evidenceType, raw)))
                .buildId(request.getBuildId())
                .evidenceType(evidenceType)
                .sourceSystem(SourceSystem.GITLAB)
                .sourceUrl(ref.sourceUri())
                .filePath(filePath)
                .symbolName(request.getSymbolName())
                .title(title)
                .summary(summary)
                .keywords(fallbackKeywords(request.getKeywords(), evidenceType, ref.projectPath()))
                .content(JsonSupport.toJson(raw))
                .rawRef(request.getRawRef())
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void enrichGitLabMetadata(EvidenceType evidenceType, JsonNode raw, Map<String, Object> metadata) {
        if (evidenceType == EvidenceType.GITLAB_DIFF) {
            int changes = raw.path("changes").isArray() ? raw.path("changes").size() : 0;
            put(metadata, "changedFiles", changes);
        } else if (evidenceType == EvidenceType.GITLAB_COMMITS) {
            put(metadata, "commitCount", raw.isArray() ? raw.size() : 0);
        } else if (evidenceType == EvidenceType.GITLAB_REVIEW_COMMENTS) {
            put(metadata, "discussionCount", raw.isArray() ? raw.size() : 0);
        } else if (evidenceType == EvidenceType.GITLAB_MERGE_REQUEST) {
            put(metadata, "state", raw.path("state").asText(""));
            put(metadata, "targetBranch", raw.path("target_branch").asText(""));
            put(metadata, "sourceBranch", raw.path("source_branch").asText(""));
        }
    }

    private String title(EvidenceType evidenceType, JsonNode raw) {
        if (evidenceType == EvidenceType.GITLAB_MERGE_REQUEST) {
            return raw.path("title").asText("GitLab merge request");
        }
        return switch (evidenceType) {
            case GITLAB_COMMITS -> "GitLab MR commits";
            case GITLAB_DIFF -> "GitLab MR diff";
            case GITLAB_REVIEW_COMMENTS -> "GitLab MR review comments";
            default -> "GitLab evidence";
        };
    }

    private String summary(EvidenceType evidenceType, JsonNode raw, GitLabMergeRequestRef ref) {
        return switch (evidenceType) {
            case GITLAB_MERGE_REQUEST -> "MR !%s `%s`, state=%s, target=%s, source=%s".formatted(
                    ref.mrIid(), raw.path("title").asText(""), raw.path("state").asText("unknown"),
                    raw.path("target_branch").asText("unknown"), raw.path("source_branch").asText("unknown"));
            case GITLAB_COMMITS -> "MR !%s has %d commits.".formatted(ref.mrIid(), raw.isArray() ? raw.size() : 0);
            case GITLAB_DIFF -> "MR !%s changes %d files.".formatted(ref.mrIid(),
                    raw.path("changes").isArray() ? raw.path("changes").size() : 0);
            case GITLAB_REVIEW_COMMENTS -> "MR !%s has %d review discussions.".formatted(ref.mrIid(),
                    raw.isArray() ? raw.size() : 0);
            default -> "GitLab evidence";
        };
    }

    private String latestCommitId(EvidenceType evidenceType, JsonNode raw) {
        if (evidenceType == EvidenceType.GITLAB_COMMITS && raw.isArray() && raw.size() > 0) {
            return raw.path(0).path("id").asText(null);
        }
        return null;
    }

    private String firstChangedFile(EvidenceType evidenceType, JsonNode raw, String projectPath) {
        if (evidenceType == EvidenceType.GITLAB_DIFF && raw.path("changes").isArray() && raw.path("changes").size() > 0) {
            JsonNode change = raw.path("changes").path(0);
            return change.path("new_path").asText(change.path("old_path").asText(projectPath));
        }
        return projectPath;
    }

    private List<String> fallbackKeywords(List<String> keywords, EvidenceType evidenceType, String projectPath) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords;
        }
        return List.of("gitlab", evidenceType.name().toLowerCase(), projectPath);
    }
}
