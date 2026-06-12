package com.codeagent.mcp;

import com.codeagent.mcp.jenkins.JenkinsBuildRef;
import com.codeagent.mcp.jenkins.JenkinsUrlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JenkinsUrlParserTest {
    @Test
    void parsesNestedJobBuildUrl() {
        JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(
                "https://jenkins.example.com/job/folder/job/user-service-ci/102/");

        assertThat(ref.jobName()).isEqualTo("folder/user-service-ci");
        assertThat(ref.buildId()).isEqualTo("102");
        assertThat(ref.sourceUri()).isEqualTo("https://jenkins.example.com/job/folder/job/user-service-ci/102");
    }
}
