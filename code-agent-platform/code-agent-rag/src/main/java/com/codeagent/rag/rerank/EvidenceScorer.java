package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;

public interface EvidenceScorer {
    double score(RagSearchRequest request, RagSearchResult result);

    default double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
