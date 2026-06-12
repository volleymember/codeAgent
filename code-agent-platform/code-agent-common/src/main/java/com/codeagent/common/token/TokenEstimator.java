package com.codeagent.common.token;

public final class TokenEstimator {
    public static final int DEFAULT_CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    public static int estimate(String value) {
        return estimate(value, DEFAULT_CHARS_PER_TOKEN);
    }

    public static int estimate(String value, int charsPerToken) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        int divisor = Math.max(1, charsPerToken);
        return Math.max(1, (int) Math.ceil((double) value.length() / divisor));
    }

    public static String truncateByTokens(String value, int maxTokens) {
        return truncateByTokens(value, maxTokens, DEFAULT_CHARS_PER_TOKEN);
    }

    public static String truncateByTokens(String value, int maxTokens, int charsPerToken) {
        if (value == null || value.isBlank() || maxTokens <= 0) {
            return "";
        }
        int maxChars = Math.max(1, maxTokens) * Math.max(1, charsPerToken);
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 24)) + "\n...[TRUNCATED]";
    }
}
