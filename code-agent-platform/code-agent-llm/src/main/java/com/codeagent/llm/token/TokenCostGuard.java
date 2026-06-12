package com.codeagent.llm.token;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.llm.config.LlmProperties;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.ManagedPrompt;
import com.codeagent.llm.model.TokenBudget;
import org.springframework.stereotype.Component;

@Component
public class TokenCostGuard {
    private final LlmProperties properties;

    public TokenCostGuard(LlmProperties properties) {
        this.properties = properties;
    }

    public ManagedPrompt fit(LlmRequest request, TokenBudget budget) {
        int maxInputTokens = Math.max(1, budget.maxInputTokens());
        int reserved = Math.max(0, properties.getTokenSafetyMargin());
        int allowedInputTokens = Math.max(1, maxInputTokens - reserved);
        int systemTokens = TokenEstimator.estimate(request.systemPrompt(), properties.getCharsPerToken());
        int userTokens = TokenEstimator.estimate(request.userPrompt(), properties.getCharsPerToken());
        int estimated = systemTokens + userTokens;
        if (estimated <= allowedInputTokens) {
            return new ManagedPrompt(request.systemPrompt(), request.userPrompt(), estimated, maxInputTokens, "PASS");
        }
        if (properties.isFailOnInputOverflow()) {
            throw new BusinessException("LLM_INPUT_TOKEN_BUDGET_EXCEEDED",
                    "Estimated input tokens %d exceeds budget %d.".formatted(estimated, allowedInputTokens));
        }
        int userBudget = Math.max(1, allowedInputTokens - systemTokens - 32);
        String trimmedUserPrompt = TokenEstimator.truncateByTokens(request.userPrompt(), userBudget, properties.getCharsPerToken())
                + "\n\n[TokenCostGuard] User prompt was truncated to fit maxInputTokens=%d.".formatted(maxInputTokens);
        int trimmedEstimate = systemTokens + TokenEstimator.estimate(trimmedUserPrompt, properties.getCharsPerToken());
        return new ManagedPrompt(request.systemPrompt(), trimmedUserPrompt, trimmedEstimate, maxInputTokens, "TRUNCATED");
    }
}
