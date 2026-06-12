package com.codeagent.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
