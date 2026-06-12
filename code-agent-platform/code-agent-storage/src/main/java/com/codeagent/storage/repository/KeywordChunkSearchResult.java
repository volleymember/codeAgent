package com.codeagent.storage.repository;

import com.codeagent.storage.entity.DocumentChunkEntity;

public record KeywordChunkSearchResult(
        DocumentChunkEntity chunk,
        double keywordScore
) {
}
