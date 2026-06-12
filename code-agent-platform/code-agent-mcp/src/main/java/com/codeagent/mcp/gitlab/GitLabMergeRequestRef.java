package com.codeagent.mcp.gitlab;

public record GitLabMergeRequestRef(
        String baseUrl,
        String projectPath,
        String mrIid,
        String sourceUri
) {
}
