package com.codeagent.core.understanding;

public record AmbiguityDecision(
        boolean needsClarification,
        String reason,
        String clarificationQuestion
) {
}
