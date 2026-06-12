package com.codeagent.rag.search;

import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.RetrievalScope;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public record RagSearchRequest(
        String projectKey,
        String moduleName,
        String branch,
        String commitId,
        String buildId,
        List<EvidenceType> evidenceTypes,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        List<RetrievalScope> retrievalScopes,
        @NotBlank String query,
        Integer topK,
        Integer vectorTopK,
        Integer keywordTopK
) {
    public RagSearchRequest {
        evidenceTypes = evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes);
        retrievalScopes = retrievalScopes == null ? List.of() : List.copyOf(retrievalScopes);
    }
}
