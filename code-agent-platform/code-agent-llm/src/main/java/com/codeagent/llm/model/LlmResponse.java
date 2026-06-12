package com.codeagent.llm.model;

public record LlmResponse(
        String requestId,
        String model,
        String content,
        long inputTokens,
        long outputTokens,
        long latencyMs
) {
}
