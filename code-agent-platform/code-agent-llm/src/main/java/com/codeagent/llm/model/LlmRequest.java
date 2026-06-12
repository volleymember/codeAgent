package com.codeagent.llm.model;

import com.codeagent.common.enums.ModelTaskType;

public record LlmRequest(
        String taskNo,
        String sessionId,
        ModelTaskType taskType,
        String systemPrompt,
        String userPrompt,
        Integer maxTokens,
        Double temperature,
        TokenBudget tokenBudget
) {
    public LlmRequest(String taskNo,
                      String sessionId,
                      ModelTaskType taskType,
                      String systemPrompt,
                      String userPrompt,
                      Integer maxTokens,
                      Double temperature) {
        this(taskNo, sessionId, taskType, systemPrompt, userPrompt, maxTokens, temperature, null);
    }
}
