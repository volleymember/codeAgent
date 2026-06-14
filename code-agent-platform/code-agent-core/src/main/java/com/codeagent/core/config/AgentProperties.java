package com.codeagent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private int maxRounds = 3;
    private int maxToolCallsPerTask = 30;
    private double minConfidence = 0.75;
    private int maxParallelAgents = 6;
    private long defaultSubtaskTimeoutMs = 30000;
    private long parallelTaskTimeoutMs = 90000;
    private int maxAgentRetries = 2;
    private long agentRetryBackoffMs = 300;
    private long queuePollTimeoutMs = 100;
    private boolean enableParallelCodeSearch = true;
    private boolean enableParallelDocumentRetrieval = true;
    private int parallelCodeSearchTopK = 6;
    private int parallelDocumentTopK = 6;
    private McpReactProperties mcpReact = new McpReactProperties();
    private Map<String, ProjectProperties> projects = new LinkedHashMap<>();

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getMaxToolCallsPerTask() {
        return maxToolCallsPerTask;
    }

    public void setMaxToolCallsPerTask(int maxToolCallsPerTask) {
        this.maxToolCallsPerTask = maxToolCallsPerTask;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public int getMaxParallelAgents() {
        return maxParallelAgents;
    }

    public void setMaxParallelAgents(int maxParallelAgents) {
        this.maxParallelAgents = maxParallelAgents;
    }

    public long getDefaultSubtaskTimeoutMs() {
        return defaultSubtaskTimeoutMs;
    }

    public void setDefaultSubtaskTimeoutMs(long defaultSubtaskTimeoutMs) {
        this.defaultSubtaskTimeoutMs = defaultSubtaskTimeoutMs;
    }

    public long getParallelTaskTimeoutMs() {
        return parallelTaskTimeoutMs;
    }

    public void setParallelTaskTimeoutMs(long parallelTaskTimeoutMs) {
        this.parallelTaskTimeoutMs = parallelTaskTimeoutMs;
    }

    public int getMaxAgentRetries() {
        return maxAgentRetries;
    }

    public void setMaxAgentRetries(int maxAgentRetries) {
        this.maxAgentRetries = maxAgentRetries;
    }

    public long getAgentRetryBackoffMs() {
        return agentRetryBackoffMs;
    }

    public void setAgentRetryBackoffMs(long agentRetryBackoffMs) {
        this.agentRetryBackoffMs = agentRetryBackoffMs;
    }

    public long getQueuePollTimeoutMs() {
        return queuePollTimeoutMs;
    }

    public void setQueuePollTimeoutMs(long queuePollTimeoutMs) {
        this.queuePollTimeoutMs = queuePollTimeoutMs;
    }

    public boolean isEnableParallelCodeSearch() {
        return enableParallelCodeSearch;
    }

    public void setEnableParallelCodeSearch(boolean enableParallelCodeSearch) {
        this.enableParallelCodeSearch = enableParallelCodeSearch;
    }

    public boolean isEnableParallelDocumentRetrieval() {
        return enableParallelDocumentRetrieval;
    }

    public void setEnableParallelDocumentRetrieval(boolean enableParallelDocumentRetrieval) {
        this.enableParallelDocumentRetrieval = enableParallelDocumentRetrieval;
    }

    public int getParallelCodeSearchTopK() {
        return parallelCodeSearchTopK;
    }

    public void setParallelCodeSearchTopK(int parallelCodeSearchTopK) {
        this.parallelCodeSearchTopK = parallelCodeSearchTopK;
    }

    public int getParallelDocumentTopK() {
        return parallelDocumentTopK;
    }

    public void setParallelDocumentTopK(int parallelDocumentTopK) {
        this.parallelDocumentTopK = parallelDocumentTopK;
    }

    public McpReactProperties getMcpReact() {
        return mcpReact;
    }

    public void setMcpReact(McpReactProperties mcpReact) {
        this.mcpReact = mcpReact == null ? new McpReactProperties() : mcpReact;
    }

    public Map<String, ProjectProperties> getProjects() {
        return projects;
    }

    public void setProjects(Map<String, ProjectProperties> projects) {
        this.projects = projects == null ? new LinkedHashMap<>() : projects;
    }

    public static class McpReactProperties {
        private boolean enabled = true;
        private int maxRounds = 3;
        private int maxToolsPerRound = 5;
        private int maxTotalToolCalls = 15;
        private int highCostMaxTimeRangeHours = 24;
        private boolean allowHighCostLongRange = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }

        public int getMaxToolsPerRound() {
            return maxToolsPerRound;
        }

        public void setMaxToolsPerRound(int maxToolsPerRound) {
            this.maxToolsPerRound = maxToolsPerRound;
        }

        public int getMaxTotalToolCalls() {
            return maxTotalToolCalls;
        }

        public void setMaxTotalToolCalls(int maxTotalToolCalls) {
            this.maxTotalToolCalls = maxTotalToolCalls;
        }

        public int getHighCostMaxTimeRangeHours() {
            return highCostMaxTimeRangeHours;
        }

        public void setHighCostMaxTimeRangeHours(int highCostMaxTimeRangeHours) {
            this.highCostMaxTimeRangeHours = highCostMaxTimeRangeHours;
        }

        public boolean isAllowHighCostLongRange() {
            return allowHighCostLongRange;
        }

        public void setAllowHighCostLongRange(boolean allowHighCostLongRange) {
            this.allowHighCostLongRange = allowHighCostLongRange;
        }
    }

    public static class ProjectProperties {
        private String projectKey;
        private String serviceName;
        private String repoName;
        private String gitlabProjectId;
        private String gitlabRepoUrl;
        private String jenkinsJobName;
        private String jenkinsPipelineName;
        private String sonarqubeProjectKey;
        private String defaultBranch;
        private String logIndex;
        private String apmServiceName;
        private String alertGroup;
        private String ownerTeam;
        private List<String> environments = List.of();
        private boolean enabled = true;

        public String getProjectKey() {
            return projectKey;
        }

        public void setProjectKey(String projectKey) {
            this.projectKey = projectKey;
        }

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getRepoName() {
            return repoName;
        }

        public void setRepoName(String repoName) {
            this.repoName = repoName;
        }

        public String getGitlabProjectId() {
            return gitlabProjectId;
        }

        public void setGitlabProjectId(String gitlabProjectId) {
            this.gitlabProjectId = gitlabProjectId;
        }

        public String getGitlabRepoUrl() {
            return gitlabRepoUrl;
        }

        public void setGitlabRepoUrl(String gitlabRepoUrl) {
            this.gitlabRepoUrl = gitlabRepoUrl;
        }

        public String getJenkinsJobName() {
            return jenkinsJobName;
        }

        public void setJenkinsJobName(String jenkinsJobName) {
            this.jenkinsJobName = jenkinsJobName;
        }

        public String getJenkinsPipelineName() {
            return jenkinsPipelineName;
        }

        public void setJenkinsPipelineName(String jenkinsPipelineName) {
            this.jenkinsPipelineName = jenkinsPipelineName;
        }

        public String getSonarqubeProjectKey() {
            return sonarqubeProjectKey;
        }

        public void setSonarqubeProjectKey(String sonarqubeProjectKey) {
            this.sonarqubeProjectKey = sonarqubeProjectKey;
        }

        public String getDefaultBranch() {
            return defaultBranch;
        }

        public void setDefaultBranch(String defaultBranch) {
            this.defaultBranch = defaultBranch;
        }

        public String getLogIndex() {
            return logIndex;
        }

        public void setLogIndex(String logIndex) {
            this.logIndex = logIndex;
        }

        public String getApmServiceName() {
            return apmServiceName;
        }

        public void setApmServiceName(String apmServiceName) {
            this.apmServiceName = apmServiceName;
        }

        public String getAlertGroup() {
            return alertGroup;
        }

        public void setAlertGroup(String alertGroup) {
            this.alertGroup = alertGroup;
        }

        public String getOwnerTeam() {
            return ownerTeam;
        }

        public void setOwnerTeam(String ownerTeam) {
            this.ownerTeam = ownerTeam;
        }

        public List<String> getEnvironments() {
            return environments;
        }

        public void setEnvironments(List<String> environments) {
            this.environments = environments == null ? List.of() : environments;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
