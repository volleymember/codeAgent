package com.codeagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integrations")
public class IntegrationsProperties {
    private GitLab gitlab = new GitLab();
    private Jenkins jenkins = new Jenkins();
    private SonarQube sonarqube = new SonarQube();

    public GitLab getGitlab() {
        return gitlab;
    }

    public void setGitlab(GitLab gitlab) {
        this.gitlab = gitlab;
    }

    public Jenkins getJenkins() {
        return jenkins;
    }

    public void setJenkins(Jenkins jenkins) {
        this.jenkins = jenkins;
    }

    public SonarQube getSonarqube() {
        return sonarqube;
    }

    public void setSonarqube(SonarQube sonarqube) {
        this.sonarqube = sonarqube;
    }

    public static class GitLab {
        private String baseUrl;
        private String token;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
        }
    }

    public static class Jenkins {
        private String baseUrl;
        private String username;
        private String token;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public boolean configured() {
            return username != null && !username.isBlank() && token != null && !token.isBlank();
        }
    }

    public static class SonarQube {
        private String baseUrl;
        private String token;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public boolean configured() {
            return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
        }
    }
}
