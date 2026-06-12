package com.codeagent.rag.search;

import java.time.LocalDateTime;

public record RagSearchResult(
        String chunkId,
        String evidenceId,
        String sourceSystem,
        String evidenceType,
        String title,
        String content,
        String sourceUri,
        String sourceUrl,
        String filePath,
        String lineRange,
        String symbolName,
        String keywords,
        Integer lineStart,
        Integer lineEnd,
        LocalDateTime createdAt,
        double score,
        double denseScore,
        double keywordScore,
        double feedbackScore,
        String matchReason
) {
}
