package com.codeagent.mcp.sonar;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class SonarQubeClient {
    private final IntegrationsProperties properties;

    public SonarQubeClient(IntegrationsProperties properties) {
        this.properties = properties;
    }

    public JsonNode getQualityGate(String projectKey) {
        return get("/api/qualitygates/project_status?projectKey={projectKey}", projectKey);
    }

    public JsonNode listIssues(String projectKey) {
        return get("/api/issues/search?componentKeys={projectKey}&resolved=false&ps=100", projectKey);
    }

    public JsonNode getMeasures(String projectKey) {
        return get("/api/measures/component?component={projectKey}&metricKeys=coverage,complexity,duplicated_lines_density,ncloc", projectKey);
    }

    private JsonNode get(String uri, String projectKey) {
        IntegrationsProperties.SonarQube sonar = properties.getSonarqube();
        if (!sonar.configured()) {
            throw new BusinessException("SONARQUBE_NOT_CONFIGURED", "SONARQUBE_BASE_URL and SONARQUBE_TOKEN are required.");
        }
        return RestClient.builder()
                .baseUrl(sonar.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(sonar.getToken()))
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build()
                .get()
                .uri(uri, projectKey)
                .retrieve()
                .body(JsonNode.class);
    }

    private String basicAuth(String token) {
        return "Basic " + Base64.getEncoder().encodeToString((token + ":").getBytes(StandardCharsets.UTF_8));
    }
}
