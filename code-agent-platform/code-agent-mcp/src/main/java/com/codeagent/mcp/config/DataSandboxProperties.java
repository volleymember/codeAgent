package com.codeagent.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.sandbox")
public class DataSandboxProperties {
    private int maxSummaryTokens = 900;
    private int maxEvidenceTokens = 260;
    private int maxKeyEvidence = 24;
    private int maxJsonFields = 30;
    private int charsPerToken = 4;

    public int getMaxSummaryTokens() {
        return maxSummaryTokens;
    }

    public void setMaxSummaryTokens(int maxSummaryTokens) {
        this.maxSummaryTokens = maxSummaryTokens;
    }

    public int getMaxEvidenceTokens() {
        return maxEvidenceTokens;
    }

    public void setMaxEvidenceTokens(int maxEvidenceTokens) {
        this.maxEvidenceTokens = maxEvidenceTokens;
    }

    public int getMaxKeyEvidence() {
        return maxKeyEvidence;
    }

    public void setMaxKeyEvidence(int maxKeyEvidence) {
        this.maxKeyEvidence = maxKeyEvidence;
    }

    public int getMaxJsonFields() {
        return maxJsonFields;
    }

    public void setMaxJsonFields(int maxJsonFields) {
        this.maxJsonFields = maxJsonFields;
    }

    public int getCharsPerToken() {
        return charsPerToken;
    }

    public void setCharsPerToken(int charsPerToken) {
        this.charsPerToken = charsPerToken;
    }
}
