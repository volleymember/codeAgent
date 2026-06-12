package com.codeagent.mcp.gitlab;

import com.codeagent.common.exception.BusinessException;

import java.net.URI;

public final class GitLabUrlParser {
    private static final String MARKER = "/-/merge_requests/";

    private GitLabUrlParser() {
    }

    public static GitLabMergeRequestRef parseMergeRequestUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            int index = path.indexOf(MARKER);
            if (index < 0) {
                throw new BusinessException("GITLAB_MR_URL_INVALID", "GitLab MR URL must contain /-/merge_requests/{iid}.");
            }
            String projectPath = stripLeadingSlash(path.substring(0, index));
            String tail = path.substring(index + MARKER.length());
            String mrIid = tail.split("/")[0];
            if (projectPath.isBlank() || mrIid.isBlank()) {
                throw new BusinessException("GITLAB_MR_URL_INVALID", "GitLab MR URL cannot resolve project path or MR IID.");
            }
            String baseUrl = uri.getScheme() + "://" + uri.getAuthority();
            return new GitLabMergeRequestRef(baseUrl, projectPath, mrIid, url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("GITLAB_MR_URL_INVALID", "Invalid GitLab MR URL.", e);
        }
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
