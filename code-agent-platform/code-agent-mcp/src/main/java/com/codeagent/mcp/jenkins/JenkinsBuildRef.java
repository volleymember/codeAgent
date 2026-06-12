package com.codeagent.mcp.jenkins;

public record JenkinsBuildRef(
        String jobName,
        String buildId,
        String sourceUri
) {
}
