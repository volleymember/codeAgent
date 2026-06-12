package com.codeagent.llm.model;

public record ManagedPrompt(
        String systemPrompt,
        String userPrompt,
        int estimatedInputTokens,
        int maxInputTokens,
        String budgetPolicy
) {
}
