package com.codeagent.rag.rerank;

public record RerankScoreBreakdown(
        double finalScore,
        double denseScore,
        double keywordScore,
        double symbolMatchScore,
        double sourcePriorityScore,
        double freshnessScore,
        double locationScore,
        double feedbackScore
) {
}
