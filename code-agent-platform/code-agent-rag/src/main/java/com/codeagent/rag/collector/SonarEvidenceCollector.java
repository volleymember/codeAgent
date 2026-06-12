package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.codeagent.mcp.sonar.SonarQubeClient;
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
public class SonarEvidenceCollector extends CollectorSupport implements EvidenceCollector {
    private final SonarQubeClient sonarQubeClient;
    private final IntegrationsProperties integrationsProperties;

    public SonarEvidenceCollector(SonarQubeClient sonarQubeClient, IntegrationsProperties integrationsProperties) {
        this.sonarQubeClient = sonarQubeClient;
        this.integrationsProperties = integrationsProperties;
    }

    @Override
    public SourceSystem sourceSystem() {
        return SourceSystem.SONARQUBE;
    }

    @Override
    public Evidence collect(IndexEvidenceRequest request) {
        String sonarProjectKey = fallback(request.getSonarProjectKey(), request.getProjectKey());
        requireText(sonarProjectKey, "SONAR_PROJECT_KEY_REQUIRED", "sonarProjectKey or projectKey is required for SonarQube evidence indexing.");
        EvidenceType evidenceType = request.getEvidenceType();
        JsonNode raw = switch (evidenceType) {
            case SONAR_QUALITY_GATE -> sonarQubeClient.getQualityGate(sonarProjectKey);
            case SONAR_ISSUES -> sonarQubeClient.listIssues(sonarProjectKey);
            case SONAR_MEASURES -> sonarQubeClient.getMeasures(sonarProjectKey);
            default -> throw new BusinessException("SONAR_EVIDENCE_TYPE_UNSUPPORTED",
                    "Unsupported SonarQube evidence type: " + evidenceType);
        };
        Map<String, Object> metadata = metadata(request);
        put(metadata, "sonarProjectKey", sonarProjectKey);
        enrichSonarMetadata(evidenceType, raw, metadata);
        log.info("Collected SonarQube evidence type={} projectKey={}", evidenceType, sonarProjectKey);
        return Evidence.builder()
                .evidenceId(nextEvidenceId())
                .taskNo(request.getTaskNo())
                .projectKey(request.getProjectKey())
                .branch(request.getBranch())
                .commitId(request.getCommitId())
                .buildId(request.getBuildId())
                .evidenceType(evidenceType)
                .sourceSystem(SourceSystem.SONARQUBE)
                .sourceUrl(sourceUrl(sonarProjectKey))
                .filePath(fallback(request.getFilePath(), "sonarqube://" + sonarProjectKey))
                .symbolName(request.getSymbolName())
                .title(title(evidenceType))
                .summary(summary(evidenceType, raw, sonarProjectKey))
                .keywords(fallbackKeywords(request.getKeywords(), evidenceType, sonarProjectKey))
                .content(JsonSupport.toJson(raw))
                .rawRef(request.getRawRef())
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void enrichSonarMetadata(EvidenceType evidenceType, JsonNode raw, Map<String, Object> metadata) {
        if (evidenceType == EvidenceType.SONAR_QUALITY_GATE) {
            put(metadata, "status", raw.path("projectStatus").path("status").asText("UNKNOWN"));
        } else if (evidenceType == EvidenceType.SONAR_ISSUES) {
            put(metadata, "issueTotal", raw.path("total").asInt(0));
        } else if (evidenceType == EvidenceType.SONAR_MEASURES) {
            for (JsonNode measure : raw.path("component").path("measures")) {
                put(metadata, measure.path("metric").asText("metric"), measure.path("value").asText(""));
            }
        }
    }

    private String title(EvidenceType evidenceType) {
        return switch (evidenceType) {
            case SONAR_QUALITY_GATE -> "SonarQube quality gate";
            case SONAR_ISSUES -> "SonarQube issues";
            case SONAR_MEASURES -> "SonarQube measures";
            default -> "SonarQube evidence";
        };
    }

    private String summary(EvidenceType evidenceType, JsonNode raw, String projectKey) {
        return switch (evidenceType) {
            case SONAR_QUALITY_GATE -> "SonarQube quality gate for `%s` status=%s".formatted(
                    projectKey, raw.path("projectStatus").path("status").asText("UNKNOWN"));
            case SONAR_ISSUES -> "SonarQube project `%s` has %d unresolved issues in query result.".formatted(
                    projectKey, raw.path("total").asInt(0));
            case SONAR_MEASURES -> "SonarQube project `%s` measures: %s".formatted(projectKey, measureSummary(raw));
            default -> "SonarQube evidence";
        };
    }

    private String measureSummary(JsonNode raw) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode measure : raw.path("component").path("measures")) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(measure.path("metric").asText("metric"))
                    .append("=")
                    .append(measure.path("value").asText("unknown"));
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private String sourceUrl(String projectKey) {
        String baseUrl = integrationsProperties.getSonarqube().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "sonarqube://project/" + projectKey;
        }
        return baseUrl.replaceAll("/+$", "") + "/dashboard?id=" + projectKey;
    }

    private List<String> fallbackKeywords(List<String> keywords, EvidenceType evidenceType, String projectKey) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords;
        }
        return List.of("sonarqube", evidenceType.name().toLowerCase(), projectKey);
    }
}
