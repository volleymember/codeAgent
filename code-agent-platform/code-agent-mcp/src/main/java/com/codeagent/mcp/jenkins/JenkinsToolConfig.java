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
    ToolExecutor jenkinsFindRecentFailedBuildsTool(JenkinsClient client,
                                                   RawOutputStore rawOutputStore,
                                                   DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.find_recent_failed_builds", "Find recent failed Jenkins builds.",
                List.of("jenkinsJobName", "timeRange"), List.of("buildNumber", "buildUrl", "commitSha", "branch", "failedStage"),
                List.of("jenkins", "ci", "build", "failure", "discovery", "time"), 900, false, "discovery"),
                rawOutputStore, dataSandboxService, request -> {
            String jobName = request.stringInput("jenkinsJobName");
            JsonNode raw = client.getJobBuilds(jobName);
            JsonNode selected = firstFailedBuild(raw);
            String buildNumber = selected.path("number").asText("");
            String buildUrl = selected.path("url").asText(buildNumber.isBlank() ? client.jobUrl(jobName) : client.buildRef(jobName, buildNumber).sourceUri());
            String commitSha = commitSha(selected);
            String branch = branch(selected);
            String summary = "Jenkins job `%s` recent failed build #%s commit=%s branch=%s".formatted(
                    jobName, buildNumber.isBlank() ? "unknown" : buildNumber, empty(commitSha), empty(branch));
            Map<String, Object> metadata = new java.util.LinkedHashMap<>();
            metadata.put("jobName", jobName);
            put(metadata, "buildNumber", buildNumber);
            put(metadata, "buildUrl", buildUrl);
            put(metadata, "commitSha", commitSha);
            put(metadata, "branch", branch);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "jenkins_failed_build_discovery", "Jenkins recent failed build", summary, 0.84,
                    buildUrl, null, metadata)));
        });
    }

    @Bean
    ToolExecutor jenkinsGetBuildStatusTool(JenkinsClient client,
                                           RawOutputStore rawOutputStore,
                                           DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("jenkins.get_build_status", "Fetch Jenkins build status.",
                List.of("jenkinsJobName", "buildNumber"), List.of("result", "buildUrl", "commitSha", "branch"),
                List.of("jenkins", "ci", "build", "status"), 500, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = buildRef(client, request);
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
                List.of("jenkinsJobName", "buildNumber"), List.of("failedStage"),
                List.of("jenkins", "ci", "stage", "fail"), 700, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = buildRef(client, request);
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
                List.of("jenkinsJobName", "buildNumber"), List.of("failedTests", "testFailure"),
                List.of("jenkins", "ci", "test", "failure", "report"), 1400, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = buildRef(client, request);
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
                List.of("jenkinsJobName", "buildNumber"), List.of("exceptionName", "filePath", "lineNumber", "commitSha"),
                List.of("jenkins", "ci", "log", "compile", "error", "exception"), 2600, true, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = buildRef(client, request);
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
                List.of("jenkinsJobName", "buildNumber"), List.of("artifacts"),
                List.of("jenkins", "ci", "artifact", "build"), 700, false, "analysis"),
                rawOutputStore, dataSandboxService, request -> {
            JenkinsBuildRef ref = buildRef(client, request);
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

    private JenkinsBuildRef buildRef(JenkinsClient client, com.codeagent.mcp.model.ToolCallRequest request) {
        return client.buildRef(request.stringInput("jenkinsJobName"), request.stringInput("buildNumber"));
    }

    private JsonNode firstFailedBuild(JsonNode raw) {
        for (JsonNode build : raw.path("builds")) {
            String result = build.path("result").asText("");
            if ("FAILURE".equalsIgnoreCase(result) || "UNSTABLE".equalsIgnoreCase(result)) {
                return build;
            }
        }
        return raw.path("builds").isArray() && raw.path("builds").size() > 0 ? raw.path("builds").path(0) : raw;
    }

    private String commitSha(JsonNode build) {
        for (JsonNode action : build.path("actions")) {
            String sha = action.path("lastBuiltRevision").path("SHA1").asText("");
            if (!sha.isBlank()) {
                return sha;
            }
        }
        for (JsonNode item : build.path("changeSet").path("items")) {
            String sha = item.path("commitId").asText("");
            if (!sha.isBlank()) {
                return sha;
            }
        }
        return "";
    }

    private String branch(JsonNode build) {
        for (JsonNode action : build.path("actions")) {
            for (JsonNode branch : action.path("lastBuiltRevision").path("branch")) {
                String name = branch.path("name").asText("");
                if (!name.isBlank()) {
                    return name.replaceFirst("^origin/", "");
                }
            }
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

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> outputFacts,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost,
                               String toolType) {
        return new ToolDefinition(name, "Jenkins", description, requiredInputs, 20000,
                tags, estimatedOutputTokens, highCost, List.of(), outputFacts, toolType, List.of(), highCost ? 8 : 3);
    }
}
