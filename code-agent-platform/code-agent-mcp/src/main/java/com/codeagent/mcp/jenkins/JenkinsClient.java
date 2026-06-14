package com.codeagent.mcp.jenkins;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.net.URLEncoder;

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

    public JsonNode getJobBuilds(String jobName) {
        return getJson(jobUrl(jobName) + "/api/json?tree=builds[number,url,result,timestamp,building,actions[lastBuiltRevision[SHA1,branch[name]]],changeSet[items[commitId]]]{0,30}");
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
        if (properties.getJenkins().getBaseUrl() == null || properties.getJenkins().getBaseUrl().isBlank()) {
            throw new BusinessException("JENKINS_NOT_CONFIGURED", "JENKINS_BASE_URL is required.");
        }
    }

    public JenkinsBuildRef buildRef(String jobName, String buildNumber) {
        return new JenkinsBuildRef(jobName, buildNumber, jobUrl(jobName) + "/" + buildNumber);
    }

    public String jobUrl(String jobName) {
        ensureConfigured();
        String base = properties.getJenkins().getBaseUrl().replaceAll("/+$", "");
        String path = java.util.Arrays.stream(jobName.split("/"))
                .filter(part -> !part.isBlank())
                .map(part -> "job/" + URLEncoder.encode(part, StandardCharsets.UTF_8))
                .reduce("", (left, right) -> left + "/" + right);
        return base + path;
    }

    private String basicAuth() {
        String raw = properties.getJenkins().getUsername() + ":" + properties.getJenkins().getToken();
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
