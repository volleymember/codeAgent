package com.codeagent.mcp.jenkins;

import com.codeagent.common.exception.BusinessException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public final class JenkinsUrlParser {
    private JenkinsUrlParser() {
    }

    public static JenkinsBuildRef parseBuildUrl(String url) {
        try {
            URI uri = URI.create(url);
            String[] segments = uri.getPath().split("/");
            List<String> jobParts = new ArrayList<>();
            String buildId = null;
            for (int i = 0; i < segments.length; i++) {
                if ("job".equals(segments[i]) && i + 1 < segments.length) {
                    jobParts.add(segments[i + 1]);
                    i++;
                    continue;
                }
                if (segments[i].matches("\\d+")) {
                    buildId = segments[i];
                }
            }
            if (jobParts.isEmpty() || buildId == null) {
                throw new BusinessException("JENKINS_BUILD_URL_INVALID", "Jenkins Build URL must contain job path and numeric build id.");
            }
            return new JenkinsBuildRef(String.join("/", jobParts), buildId, trimTrailingSlash(url));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("JENKINS_BUILD_URL_INVALID", "Invalid Jenkins Build URL.", e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
