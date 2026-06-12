package com.codeagent.llm.model;

public record TokenBudget(
        int maxInputTokens,
        int maxOutputTokens,
        int maxEvidenceTokens
) {
}
