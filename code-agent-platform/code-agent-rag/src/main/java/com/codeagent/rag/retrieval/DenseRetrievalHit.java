package com.codeagent.rag.retrieval;

import com.codeagent.storage.entity.DocumentChunkEntity;

public record DenseRetrievalHit(
        DocumentChunkEntity chunk,
        double denseScore
) {
}
