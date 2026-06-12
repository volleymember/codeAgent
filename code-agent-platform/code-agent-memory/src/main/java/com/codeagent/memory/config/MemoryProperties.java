package com.codeagent.memory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private String globalProjectKey = "GLOBAL";
    private int maxCoreRules = 20;
    private int maxEpisodeRecall = 6;
    private double minRecallScore = 0.18;
    private long workingTtlHours = 12;
    private int maxAgentNotes = 80;
    private int maxContextTokens = 1800;
    private int maxCoreRuleTokens = 120;
    private int maxEpisodeTokens = 240;
    private int maxAgentNoteTokens = 80;
    private int charsPerToken = 4;

    public String getGlobalProjectKey() {
        return globalProjectKey;
    }

    public void setGlobalProjectKey(String globalProjectKey) {
        this.globalProjectKey = globalProjectKey;
    }

    public int getMaxCoreRules() {
        return maxCoreRules;
    }

    public void setMaxCoreRules(int maxCoreRules) {
        this.maxCoreRules = maxCoreRules;
    }

    public int getMaxEpisodeRecall() {
        return maxEpisodeRecall;
    }

    public void setMaxEpisodeRecall(int maxEpisodeRecall) {
        this.maxEpisodeRecall = maxEpisodeRecall;
    }

    public double getMinRecallScore() {
        return minRecallScore;
    }

    public void setMinRecallScore(double minRecallScore) {
        this.minRecallScore = minRecallScore;
    }

    public long getWorkingTtlHours() {
        return workingTtlHours;
    }

    public void setWorkingTtlHours(long workingTtlHours) {
        this.workingTtlHours = workingTtlHours;
    }

    public int getMaxAgentNotes() {
        return maxAgentNotes;
    }

    public void setMaxAgentNotes(int maxAgentNotes) {
        this.maxAgentNotes = maxAgentNotes;
    }

    public int getMaxContextTokens() {
        return maxContextTokens;
    }

    public void setMaxContextTokens(int maxContextTokens) {
        this.maxContextTokens = maxContextTokens;
    }

    public int getMaxCoreRuleTokens() {
        return maxCoreRuleTokens;
    }

    public void setMaxCoreRuleTokens(int maxCoreRuleTokens) {
        this.maxCoreRuleTokens = maxCoreRuleTokens;
    }

    public int getMaxEpisodeTokens() {
        return maxEpisodeTokens;
    }

    public void setMaxEpisodeTokens(int maxEpisodeTokens) {
        this.maxEpisodeTokens = maxEpisodeTokens;
    }

    public int getMaxAgentNoteTokens() {
        return maxAgentNoteTokens;
    }

    public void setMaxAgentNoteTokens(int maxAgentNoteTokens) {
        this.maxAgentNoteTokens = maxAgentNoteTokens;
    }

    public int getCharsPerToken() {
        return charsPerToken;
    }

    public void setCharsPerToken(int charsPerToken) {
        this.charsPerToken = charsPerToken;
    }
}
