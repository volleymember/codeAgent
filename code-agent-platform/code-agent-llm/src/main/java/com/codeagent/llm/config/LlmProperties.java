package com.codeagent.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private String provider = "deepseek";
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String defaultModel = "deepseek-v4-pro";
    private int maxRounds = 3;
    private int maxLlmCallsPerTask = 8;
    private int maxInputTokens = 16000;
    private int maxOutputTokens = 4000;
    private int maxEvidenceTokens = 5000;
    private int tokenSafetyMargin = 512;
    private int charsPerToken = 4;
    private boolean failOnInputOverflow = false;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public int getMaxLlmCallsPerTask() {
        return maxLlmCallsPerTask;
    }

    public void setMaxLlmCallsPerTask(int maxLlmCallsPerTask) {
        this.maxLlmCallsPerTask = maxLlmCallsPerTask;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxEvidenceTokens() {
        return maxEvidenceTokens;
    }

    public void setMaxEvidenceTokens(int maxEvidenceTokens) {
        this.maxEvidenceTokens = maxEvidenceTokens;
    }

    public int getTokenSafetyMargin() {
        return tokenSafetyMargin;
    }

    public void setTokenSafetyMargin(int tokenSafetyMargin) {
        this.tokenSafetyMargin = tokenSafetyMargin;
    }

    public int getCharsPerToken() {
        return charsPerToken;
    }

    public void setCharsPerToken(int charsPerToken) {
        this.charsPerToken = charsPerToken;
    }

    public boolean isFailOnInputOverflow() {
        return failOnInputOverflow;
    }

    public void setFailOnInputOverflow(boolean failOnInputOverflow) {
        this.failOnInputOverflow = failOnInputOverflow;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
