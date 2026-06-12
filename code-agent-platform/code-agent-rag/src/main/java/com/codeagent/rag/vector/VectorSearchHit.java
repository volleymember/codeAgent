package com.codeagent.rag.vector;

import java.util.Map;

public record VectorSearchHit(
        String id,
        double score,
        Map<String, Object> metadata
) {
}
