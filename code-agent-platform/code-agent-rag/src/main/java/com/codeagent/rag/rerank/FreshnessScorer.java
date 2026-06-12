package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class FreshnessScorer implements EvidenceScorer {
    @Override
    public double score(RagSearchRequest request, RagSearchResult result) {
        if (result.createdAt() == null) {
            return 0.5;
        }
        long ageDays = Math.max(0, Duration.between(result.createdAt(), LocalDateTime.now()).toDays());
        if (ageDays <= 1) {
            return 1.0;
        }
        if (ageDays <= 7) {
            return 0.85;
        }
        if (ageDays <= 30) {
            return 0.65;
        }
        if (ageDays <= 90) {
            return 0.42;
        }
        if (ageDays <= 180) {
            return 0.22;
        }
        return 0.1;
    }
}
