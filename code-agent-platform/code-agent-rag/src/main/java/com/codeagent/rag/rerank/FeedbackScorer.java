package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

@Component
public class FeedbackScorer implements EvidenceScorer {
    private static final double DEFAULT_FEEDBACK_SCORE = 0.5;

    @Override
    public double score(RagSearchRequest request, RagSearchResult result) {
        double score = result.feedbackScore() > 0.0 ? result.feedbackScore() : DEFAULT_FEEDBACK_SCORE;
        return clamp(score);
    }
}
