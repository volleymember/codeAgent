package com.codeagent.mcp.jenkins;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JenkinsClient {
    private final IntegrationsProperties properties;
    private final RestClient restClient = RestClient.create();

    public JenkinsClient(IntegrationsProperties properties) {
        this.properties = properties;
    }

    public JsonNode getBuildStatus(JenkinsBuildRef ref) {
        return getJson(ref.sourceUri() + "/api/json");
    }

    public JsonNode getTestReport(JenkinsBuildRef ref) {
        return getJson(ref.sourceUri() + "/testReport/api/json");
    }

    public JsonNode getPipelineDescription(JenkinsBuildRef ref) {
        return getJson(ref.sourceUri() + "/wfapi/describe");
    }

    public String getConsoleLog(JenkinsBuildRef ref) {
        ensureConfigured();
        return restClient.get()
                .uri(ref.sourceUri() + "/consoleText")
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .retrieve()
                .body(String.class);
    }

    private JsonNode getJson(String uri) {
        ensureConfigured();
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(JsonNode.class);
    }

    private void ensureConfigured() {
        if (!properties.getJenkins().configured()) {
            throw new BusinessException("JENKINS_NOT_CONFIGURED", "JENKINS_USERNAME and JENKINS_TOKEN are required.");
        }
    }

    private String basicAuth() {
        String raw = properties.getJenkins().getUsername() + ":" + properties.getJenkins().getToken();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
