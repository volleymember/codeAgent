package com.codeagent.mcp.jenkins;

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
public class JenkinsToolConfig {
    @Bean
    ToolExecutor jenkinsGetBuildStatusTool(JenkinsClient client,
                                           RawOutputStore rawOutputStore,
                                           DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_build_status", "Fetch Jenkins build status.",
                List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "build", "status"), 500, false),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.stringInput("jenkinsBuildUrl"));
            JsonNode raw = client.getBuildStatus(ref);
            String result = raw.path("result").asText("RUNNING_OR_UNKNOWN");
            String summary = "Jenkins build `%s` #%s result=%s, building=%s".formatted(
                    ref.jobName(), ref.buildId(), result, raw.path("building").asBoolean(false));
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "jenkins_build_status", "Jenkins build status", summary, 0.86, ref.sourceUri(), null,
                    Map.of("jobName", ref.jobName(), "buildId", ref.buildId()))));
        });
    }

    @Bean
    ToolExecutor jenkinsGetFailedStageTool(JenkinsClient client,
                                           RawOutputStore rawOutputStore,
                                           DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_failed_stage", "Fetch Jenkins pipeline stages.",
                List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "stage", "fail"), 700, false),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.stringInput("jenkinsBuildUrl"));
            JsonNode raw = client.getPipelineDescription(ref);
            String failed = "unknown";
            for (JsonNode stage : raw.path("stages")) {
                if ("FAILED".equalsIgnoreCase(stage.path("status").asText())) {
                    failed = stage.path("name").asText("unknown");
                    break;
                }
            }
            String summary = "Jenkins build `%s` #%s failed stage=%s".formatted(ref.jobName(), ref.buildId(), failed);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "jenkins_failed_stage", "Jenkins failed stage", summary, 0.82, ref.sourceUri(), null,
                    Map.of("jobName", ref.jobName(), "buildId", ref.buildId(), "failedStage", failed))));
        });
    }

    @Bean
    ToolExecutor jenkinsGetTestReportTool(JenkinsClient client,
                                          RawOutputStore rawOutputStore,
                                          DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_test_report", "Fetch Jenkins test report.",
                List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "test", "failure", "report"), 1400, false),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.stringInput("jenkinsBuildUrl"));
            JsonNode raw = client.getTestReport(ref);
            int failCount = raw.path("failCount").asInt(0);
            int skipCount = raw.path("skipCount").asInt(0);
            int totalCount = raw.path("totalCount").asInt(0);
            String firstFailure = firstFailedCase(raw);
            String summary = "Jenkins test report total=%d, failed=%d, skipped=%d. First failure=%s".formatted(
                    totalCount, failCount, skipCount, firstFailure);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "jenkins_test_report", "Jenkins test report", summary, 0.91, ref.sourceUri() + "/testReport", null,
                    Map.of("jobName", ref.jobName(), "buildId", ref.buildId(), "failCount", failCount))));
        });
    }

    @Bean
    ToolExecutor jenkinsGetConsoleLogSummaryTool(JenkinsClient client,
                                                 RawOutputStore rawOutputStore,
                                                 DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_console_log_summary", "Fetch and summarize Jenkins console log.",
                List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "log", "compile", "error", "exception"), 2600, true),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.stringInput("jenkinsBuildUrl"));
            String raw = client.getConsoleLog(ref);
            String summary = JenkinsLogSandbox.summarize(raw);
            return new ToolExecutionPayload(Map.of("consoleLog", raw), summary, List.of(new EvidenceItem(
                    "jenkins_console_log_summary", "Jenkins console log summary", summary, 0.84,
                    ref.sourceUri() + "/console", null,
                    Map.of("jobName", ref.jobName(), "buildId", ref.buildId()))));
        });
    }

    @Bean
    ToolExecutor jenkinsGetArtifactsTool(JenkinsClient client,
                                         RawOutputStore rawOutputStore,
                                         DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_artifacts", "Fetch Jenkins build artifacts metadata.",
                List.of("jenkinsBuildUrl"), List.of("jenkins", "ci", "artifact", "build"), 700, false),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.stringInput("jenkinsBuildUrl"));
            JsonNode raw = client.getBuildStatus(ref).path("artifacts");
            int count = raw.isArray() ? raw.size() : 0;
            String summary = "Jenkins build `%s` #%s has %d artifacts.".formatted(ref.jobName(), ref.buildId(), count);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "jenkins_artifacts", "Jenkins build artifacts", summary, 0.65, ref.sourceUri() + "/artifact", null,
                    Map.of("jobName", ref.jobName(), "buildId", ref.buildId(), "artifactCount", count))));
        });
    }

    private String firstFailedCase(JsonNode report) {
        for (JsonNode suite : report.path("suites")) {
            for (JsonNode kase : suite.path("cases")) {
                if ("FAILED".equalsIgnoreCase(kase.path("status").asText())
                        || !kase.path("errorDetails").asText("").isBlank()) {
                    return suite.path("name").asText("") + "#" + kase.path("name").asText("");
                }
            }
        }
        return "none";
    }

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost) {
        return new ToolDefinition(name, "Jenkins", description, requiredInputs, 20000,
                tags, estimatedOutputTokens, highCost);
    }
}
