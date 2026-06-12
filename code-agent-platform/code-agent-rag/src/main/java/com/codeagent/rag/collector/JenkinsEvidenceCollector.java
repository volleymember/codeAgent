package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.mcp.jenkins.JenkinsBuildRef;
import com.codeagent.mcp.jenkins.JenkinsClient;
import com.codeagent.mcp.jenkins.JenkinsUrlParser;
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
public class JenkinsEvidenceCollector extends CollectorSupport implements EvidenceCollector {
    private final JenkinsClient jenkinsClient;

    public JenkinsEvidenceCollector(JenkinsClient jenkinsClient) {
        this.jenkinsClient = jenkinsClient;
    }

    @Override
    public SourceSystem sourceSystem() {
        return SourceSystem.JENKINS;
    }

    @Override
    public Evidence collect(IndexEvidenceRequest request) {
        requireText(request.getJenkinsBuildUrl(), "JENKINS_BUILD_URL_REQUIRED", "jenkinsBuildUrl is required for Jenkins evidence indexing.");
        JenkinsBuildRef ref = JenkinsUrlParser.parseBuildUrl(request.getJenkinsBuildUrl());
        EvidenceType evidenceType = request.getEvidenceType();
        Object raw = switch (evidenceType) {
            case JENKINS_BUILD_STATUS, JENKINS_ARTIFACTS -> jenkinsClient.getBuildStatus(ref);
            case JENKINS_TEST_REPORT -> jenkinsClient.getTestReport(ref);
            case JENKINS_CONSOLE_LOG -> jenkinsClient.getConsoleLog(ref);
            default -> throw new BusinessException("JENKINS_EVIDENCE_TYPE_UNSUPPORTED",
                    "Unsupported Jenkins evidence type: " + evidenceType);
        };
        Map<String, Object> metadata = metadata(request);
        put(metadata, "jobName", ref.jobName());
        put(metadata, "buildId", ref.buildId());
        enrichJenkinsMetadata(evidenceType, raw, metadata);
        String title = title(evidenceType);
        String summary = summary(evidenceType, raw, ref);
        log.info("Collected Jenkins evidence type={} projectKey={} source={}", evidenceType, request.getProjectKey(), ref.sourceUri());
        return Evidence.builder()
                .evidenceId(nextEvidenceId())
                .taskNo(request.getTaskNo())
                .projectKey(request.getProjectKey())
                .branch(request.getBranch())
                .commitId(request.getCommitId())
                .buildId(fallback(request.getBuildId(), ref.buildId()))
                .evidenceType(evidenceType)
                .sourceSystem(SourceSystem.JENKINS)
                .sourceUrl(sourceUrl(evidenceType, ref))
                .filePath(fallback(request.getFilePath(), "jenkins://" + ref.jobName() + "/" + ref.buildId()))
                .symbolName(request.getSymbolName())
                .title(title)
                .summary(summary)
                .keywords(fallbackKeywords(request.getKeywords(), evidenceType, ref.jobName()))
                .content(raw instanceof String text ? text : JsonSupport.toJson(raw))
                .rawRef(request.getRawRef())
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void enrichJenkinsMetadata(EvidenceType evidenceType, Object raw, Map<String, Object> metadata) {
        if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_TEST_REPORT) {
            put(metadata, "failCount", json.path("failCount").asInt(0));
            put(metadata, "skipCount", json.path("skipCount").asInt(0));
            put(metadata, "totalCount", json.path("totalCount").asInt(0));
            put(metadata, "firstFailure", firstFailedCase(json));
        } else if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_BUILD_STATUS) {
            put(metadata, "result", json.path("result").asText("RUNNING_OR_UNKNOWN"));
            put(metadata, "building", json.path("building").asBoolean(false));
        } else if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_ARTIFACTS) {
            JsonNode artifacts = json.path("artifacts");
            put(metadata, "artifactCount", artifacts.isArray() ? artifacts.size() : 0);
        }
    }

    private String title(EvidenceType evidenceType) {
        return switch (evidenceType) {
            case JENKINS_BUILD_STATUS -> "Jenkins build status";
            case JENKINS_TEST_REPORT -> "Jenkins test report";
            case JENKINS_CONSOLE_LOG -> "Jenkins console log";
            case JENKINS_ARTIFACTS -> "Jenkins artifacts";
            default -> "Jenkins evidence";
        };
    }

    private String summary(EvidenceType evidenceType, Object raw, JenkinsBuildRef ref) {
        if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_BUILD_STATUS) {
            return "Jenkins build `%s` #%s result=%s, building=%s".formatted(
                    ref.jobName(), ref.buildId(), json.path("result").asText("RUNNING_OR_UNKNOWN"),
                    json.path("building").asBoolean(false));
        }
        if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_TEST_REPORT) {
            return "Jenkins test report total=%d, failed=%d, skipped=%d. First failure=%s".formatted(
                    json.path("totalCount").asInt(0), json.path("failCount").asInt(0),
                    json.path("skipCount").asInt(0), firstFailedCase(json));
        }
        if (raw instanceof JsonNode json && evidenceType == EvidenceType.JENKINS_ARTIFACTS) {
            JsonNode artifacts = json.path("artifacts");
            return "Jenkins build `%s` #%s has %d artifacts.".formatted(
                    ref.jobName(), ref.buildId(), artifacts.isArray() ? artifacts.size() : 0);
        }
        return "Jenkins console log for `%s` #%s.".formatted(ref.jobName(), ref.buildId());
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

    private String sourceUrl(EvidenceType evidenceType, JenkinsBuildRef ref) {
        return switch (evidenceType) {
            case JENKINS_TEST_REPORT -> ref.sourceUri() + "/testReport";
            case JENKINS_CONSOLE_LOG -> ref.sourceUri() + "/console";
            case JENKINS_ARTIFACTS -> ref.sourceUri() + "/artifact";
            default -> ref.sourceUri();
        };
    }

    private List<String> fallbackKeywords(List<String> keywords, EvidenceType evidenceType, String jobName) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords;
        }
        return List.of("jenkins", evidenceType.name().toLowerCase(), jobName);
    }
}
