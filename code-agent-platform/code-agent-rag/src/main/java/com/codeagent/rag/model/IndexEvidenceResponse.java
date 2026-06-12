package com.codeagent.rag.model;

import lombok.Builder;

import java.util.List;

@Builder
public record IndexEvidenceResponse(
        String evidenceId,
        boolean skipped,
        String contentHash,
        String objectId,
        Long fileSize,
        int chunkCount,
        List<String> chunkIds,
        List<String> vectorIds
) {
    public IndexEvidenceResponse {
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
        vectorIds = vectorIds == null ? List.of() : List.copyOf(vectorIds);
    }
}
