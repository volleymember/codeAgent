package com.codeagent.mcp;

import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.model.ToolRouteCandidate;
import com.codeagent.mcp.model.ToolRouteRequest;
import com.codeagent.mcp.router.ToolSelectionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSelectionServiceTest {
    private final ToolSelectionService service = new ToolSelectionService();

    @Test
    void routesCiFailureToHighSignalJenkinsTools() {
        List<ToolDefinition> definitions = List.of(
                tool("jenkins.get_build_status", "Jenkins", List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "build"), 500, false),
                tool("jenkins.get_test_report", "Jenkins", List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "test", "failure"), 1400, false),
                tool("jenkins.get_console_log_summary", "Jenkins", List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "log", "error"), 2600, true),
                tool("gitlab.get_merge_request_diff", "GitLab", List.of("gitlabMrUrl"), List.of("git", "diff", "code"), 2400, true),
                tool("sonarqube.get_quality_gate", "SonarQube", List.of("sonarqubeProjectKey"), List.of("sonar", "quality"), 700, false)
        );
        ToolRouteRequest request = new ToolRouteRequest(
                "CI_FAILURE_ANALYSIS",
                "payment-service",
                "build failed after merge request",
                Map.of(
                        "jenkinsBuildUrl", "https://jenkins.example.com/job/payment/15",
                        "gitlabMrUrl", "https://gitlab.example.com/backend/payment/-/merge_requests/8"
                ),
                4
        );

        List<ToolRouteCandidate> candidates = service.route(request, definitions);

        assertThat(candidates).extracting(ToolRouteCandidate::toolName)
                .contains("jenkins.get_test_report", "jenkins.get_console_log_summary", "jenkins.get_build_status");
        assertThat(candidates.stream()
                .filter(candidate -> "jenkins.get_test_report".equals(candidate.toolName()))
                .findFirst()
                .orElseThrow()
                .required()).isTrue();
        assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.score()).isBetween(0.0, 1.0));
    }

    private ToolDefinition tool(String name,
                                String platform,
                                List<String> inputs,
                                List<String> tags,
                                int estimatedOutputTokens,
                                boolean highCost) {
        return new ToolDefinition(name, platform, "test tool", inputs, 1000, tags, estimatedOutputTokens, highCost);
    }
}
