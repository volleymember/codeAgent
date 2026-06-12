package com.codeagent.rag.retrieval;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.config.RagProperties;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.RetrievalScope;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.storage.entity.DocumentChunkEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetadataFilterBuilder {
    private final RagProperties ragProperties;

    public MetadataFilterBuilder(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public MetadataFilter build(RagSearchRequest request) {
        validate(request);
        List<String> evidenceTypes = request.evidenceTypes().stream()
                .map(EvidenceType::name)
                .toList();
        boolean vectorEnabled = request.retrievalScopes().isEmpty()
                || request.retrievalScopes().contains(RetrievalScope.VECTOR);
        boolean keywordEnabled = request.retrievalScopes().isEmpty()
                || request.retrievalScopes().contains(RetrievalScope.KEYWORD);
        int finalLimit = positiveOrDefault(request.topK(), Math.max(1, ragProperties.getFinalEvidenceLimit()));
        int vectorLimit = positiveOrDefault(request.vectorTopK(), Math.max(finalLimit, ragProperties.getVectorTopK()));
        int keywordLimit = positiveOrDefault(request.keywordTopK(), Math.max(finalLimit, ragProperties.getBm25TopK()));
        Specification<DocumentChunkEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectKey"), request.projectKey()));
            addEquals(predicates, cb, root.get("moduleName"), request.moduleName());
            addEquals(predicates, cb, root.get("branch"), request.branch());
            addEquals(predicates, cb, root.get("commitId"), request.commitId());
            addEquals(predicates, cb, root.get("buildId"), request.buildId());
            if (!evidenceTypes.isEmpty()) {
                predicates.add(root.get("evidenceType").in(evidenceTypes));
            }
            if (request.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.createdFrom()));
            }
            if (request.createdTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.createdTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("projectKey", request.projectKey());
        put(summary, "moduleName", request.moduleName());
        put(summary, "branch", request.branch());
        put(summary, "commitId", request.commitId());
        put(summary, "buildId", request.buildId());
        if (!evidenceTypes.isEmpty()) {
            summary.put("evidenceTypes", evidenceTypes);
        }
        if (request.createdFrom() != null) {
            summary.put("createdFrom", request.createdFrom());
        }
        if (request.createdTo() != null) {
            summary.put("createdTo", request.createdTo());
        }
        summary.put("vectorEnabled", vectorEnabled);
        summary.put("keywordEnabled", keywordEnabled);
        summary.put("finalLimit", finalLimit);
        summary.put("vectorLimit", vectorLimit);
        summary.put("keywordLimit", keywordLimit);
        return new MetadataFilter(specification, request.projectKey(), request.moduleName(), request.branch(),
                request.commitId(), request.buildId(), evidenceTypes, request.createdFrom(), request.createdTo(),
                vectorEnabled, keywordEnabled, finalLimit, vectorLimit, keywordLimit, summary);
    }

    private void validate(RagSearchRequest request) {
        if (request == null) {
            throw new BusinessException("RAG_SEARCH_REQUEST_EMPTY", "RAG search request must not be null.");
        }
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            throw new BusinessException("PROJECT_KEY_REQUIRED", "projectKey must not be empty.");
        }
        if (request.query() == null || request.query().isBlank()) {
            throw new BusinessException("RAG_QUERY_REQUIRED", "query must not be empty.");
        }
        if (request.createdFrom() != null && request.createdTo() != null
                && request.createdFrom().isAfter(request.createdTo())) {
            throw new BusinessException("RAG_TIME_RANGE_INVALID", "createdFrom must be before or equal to createdTo.");
        }
    }

    private int positiveOrDefault(Integer value, int fallback) {
        return value != null && value > 0 ? value : Math.max(1, fallback);
    }

    private void addEquals(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                           jakarta.persistence.criteria.Path<String> path, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(cb.equal(path, value));
        }
    }

    private void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
