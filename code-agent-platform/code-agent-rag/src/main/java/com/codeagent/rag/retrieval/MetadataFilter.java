package com.codeagent.rag.retrieval;

import com.codeagent.storage.entity.DocumentChunkEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MetadataFilter(
        Specification<DocumentChunkEntity> specification,
        String projectKey,
        String moduleName,
        String branch,
        String commitId,
        String buildId,
        List<String> evidenceTypes,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        boolean vectorEnabled,
        boolean keywordEnabled,
        int finalLimit,
        int vectorLimit,
        int keywordLimit,
        Map<String, Object> summary
) {
    public MetadataFilter {
        evidenceTypes = evidenceTypes == null ? List.of() : List.copyOf(evidenceTypes);
        summary = summary == null ? Map.of() : Map.copyOf(summary);
    }
}
