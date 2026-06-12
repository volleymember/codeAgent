package com.codeagent.rag.retrieval;

import com.codeagent.storage.entity.DocumentChunkEntity;

public record KeywordRetrievalHit(
        DocumentChunkEntity chunk,
        double keywordScore
) {
}
