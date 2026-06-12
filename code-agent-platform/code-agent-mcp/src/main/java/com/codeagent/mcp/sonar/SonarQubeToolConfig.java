package com.codeagent.mcp.sonar;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.config.IntegrationsProperties;
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
public class SonarQubeToolConfig {
    private final IntegrationsProperties properties;

    public SonarQubeToolConfig(IntegrationsProperties properties) {
        this.properties = properties;
    }

    @Bean
    ToolExecutor sonarqubeGetQualityGateTool(SonarQubeClient client,
                                             RawOutputStore rawOutputStore,
                                             DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("sonarqube.get_quality_gate", "Fetch SonarQube quality gate.",
                List.of("sonarqubeProjectKey"), List.of("sonar", "quality", "gate", "risk"), 700, false),
                rawOutputStore, dataSandboxService, request -> {
            String projectKey = request.stringInput("sonarqubeProjectKey");
            JsonNode raw = client.getQualityGate(projectKey);
            String status = raw.path("projectStatus").path("status").asText("UNKNOWN");
            String summary = "SonarQube quality gate for `%s` status=%s".formatted(projectKey, status);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "sonarqube_quality_gate", "SonarQube quality gate", summary, 0.88,
                    sonarProjectUri(projectKey), null, Map.of("projectKey", projectKey, "status", status))));
        });
    }

    @Bean
    ToolExecutor sonarqubeListIssuesTool(SonarQubeClient client,
                                         RawOutputStore rawOutputStore,
                                         DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("sonarqube.list_issues", "Fetch unresolved SonarQube issues.",
                List.of("sonarqubeProjectKey"), List.of("sonar", "quality", "issue", "bug", "vulnerability"), 1600, false),
                rawOutputStore, dataSandboxService, request -> {
            String projectKey = request.stringInput("sonarqubeProjectKey");
            JsonNode raw = client.listIssues(projectKey);
            int total = raw.path("total").asInt(0);
            String summary = "SonarQube project `%s` has %d unresolved issues in query result.".formatted(projectKey, total);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    "sonarqube_issues", "SonarQube unresolved issues", summary, 0.82,
                    sonarProjectUri(projectKey), null, Map.of("projectKey", projectKey, "issueTotal", total))));
        });
    }

    @Bean
    ToolExecutor sonarqubeGetCoverageTool(SonarQubeClient client,
                                          RawOutputStore rawOutputStore,
                                          DataSandboxService dataSandboxService) {
        return measureTool(client, rawOutputStore, "sonarqube.get_coverage", "coverage",
                "SonarQube coverage", "sonarqube_coverage", List.of("sonar", "quality", "coverage", "test"),
                dataSandboxService);
    }

    @Bean
    ToolExecutor sonarqubeGetComplexityTool(SonarQubeClient client,
                                            RawOutputStore rawOutputStore,
                                            DataSandboxService dataSandboxService) {
        return measureTool(client, rawOutputStore, "sonarqube.get_complexity", "complexity",
                "SonarQube complexity", "sonarqube_complexity", List.of("sonar", "quality", "complexity", "maintainability"),
                dataSandboxService);
    }

    @Bean
    ToolExecutor sonarqubeGetDuplicationsTool(SonarQubeClient client,
                                              RawOutputStore rawOutputStore,
                                              DataSandboxService dataSandboxService) {
        return measureTool(client, rawOutputStore, "sonarqube.get_duplications", "duplicated_lines_density",
                "SonarQube duplications", "sonarqube_duplications", List.of("sonar", "quality", "duplication", "maintainability"),
                dataSandboxService);
    }

    private ToolExecutor measureTool(SonarQubeClient client, RawOutputStore rawOutputStore, String toolName,
                                     String metricKey, String title, String sourceType, List<String> tags,
                                     DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def(toolName, "Fetch SonarQube measure " + metricKey + ".",
                List.of("sonarqubeProjectKey"), tags, 700, false),
                rawOutputStore, dataSandboxService, request -> {
            String projectKey = request.stringInput("sonarqubeProjectKey");
            JsonNode raw = client.getMeasures(projectKey);
            String value = findMeasure(raw, metricKey);
            String summary = "%s for `%s` is %s".formatted(title, projectKey, value);
            return new ToolExecutionPayload(raw, summary, List.of(new EvidenceItem(
                    sourceType, title, summary, 0.76, sonarProjectUri(projectKey), null,
                    Map.of("projectKey", projectKey, "metric", metricKey, "value", value))));
        });
    }

    private String findMeasure(JsonNode raw, String metricKey) {
        for (JsonNode measure : raw.path("component").path("measures")) {
            if (metricKey.equals(measure.path("metric").asText())) {
                return measure.path("value").asText("unknown");
            }
        }
        return "unknown";
    }

    private String sonarProjectUri(String projectKey) {
        String baseUrl = properties.getSonarqube().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "sonarqube://project/" + projectKey;
        }
        return baseUrl.replaceAll("/+$", "") + "/dashboard?id=" + projectKey;
    }

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost) {
        return new ToolDefinition(name, "SonarQube", description, requiredInputs, 15000,
                tags, estimatedOutputTokens, highCost);
    }
}
