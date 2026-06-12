package com.codeagent.storage.repository;

import com.codeagent.storage.entity.DocumentChunkEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DocumentChunkSearchRepositoryImpl implements DocumentChunkSearchRepository {
    private final EntityManager entityManager;

    public DocumentChunkSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<KeywordChunkSearchResult> searchByKeyword(String queryText,
                                                          String booleanQuery,
                                                          String likeQuery,
                                                          String projectKey,
                                                          String moduleName,
                                                          String branch,
                                                          String commitId,
                                                          String buildId,
                                                          List<String> evidenceTypes,
                                                          LocalDateTime createdFrom,
                                                          LocalDateTime createdTo,
                                                          int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("""
                SELECT dc.chunk_id,
                       (
                           MATCH(dc.symbol_name, dc.title, dc.keywords, dc.content)
                               AGAINST (:queryText IN NATURAL LANGUAGE MODE)
                           + CASE WHEN LOWER(COALESCE(dc.symbol_name, '')) LIKE :likeQuery THEN 5 ELSE 0 END
                           + CASE WHEN LOWER(COALESCE(dc.keywords, '')) LIKE :likeQuery THEN 4 ELSE 0 END
                           + CASE WHEN LOWER(COALESCE(dc.title, '')) LIKE :likeQuery THEN 2 ELSE 0 END
                           + CASE WHEN LOWER(COALESCE(dc.content, '')) LIKE :likeQuery THEN 1 ELSE 0 END
                           + CASE WHEN LOWER(COALESCE(dc.file_path, '')) LIKE :likeQuery THEN 1 ELSE 0 END
                       ) AS keyword_score
                FROM document_chunk dc
                WHERE dc.project_key = :projectKey
                  AND (
                       MATCH(dc.symbol_name, dc.title, dc.keywords, dc.content)
                           AGAINST (:booleanQuery IN BOOLEAN MODE)
                       OR LOWER(COALESCE(dc.symbol_name, '')) LIKE :likeQuery
                       OR LOWER(COALESCE(dc.keywords, '')) LIKE :likeQuery
                       OR LOWER(COALESCE(dc.title, '')) LIKE :likeQuery
                       OR LOWER(COALESCE(dc.content, '')) LIKE :likeQuery
                       OR LOWER(COALESCE(dc.file_path, '')) LIKE :likeQuery
                  )
                """);
        params.put("queryText", queryText);
        params.put("booleanQuery", booleanQuery);
        params.put("likeQuery", likeQuery);
        params.put("projectKey", projectKey);
        appendEquals(sql, params, "dc.module_name", "moduleName", moduleName);
        appendEquals(sql, params, "dc.branch", "branch", branch);
        appendEquals(sql, params, "dc.commit_id", "commitId", commitId);
        appendEquals(sql, params, "dc.build_id", "buildId", buildId);
        if (evidenceTypes != null && !evidenceTypes.isEmpty()) {
            sql.append(" AND dc.evidence_type IN (:evidenceTypes)");
            params.put("evidenceTypes", evidenceTypes);
        }
        if (createdFrom != null) {
            sql.append(" AND dc.created_at >= :createdFrom");
            params.put("createdFrom", createdFrom);
        }
        if (createdTo != null) {
            sql.append(" AND dc.created_at <= :createdTo");
            params.put("createdTo", createdTo);
        }
        sql.append(" ORDER BY keyword_score DESC, dc.id DESC LIMIT :limit");
        params.put("limit", Math.max(1, limit));

        Query query = entityManager.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        List<?> rows = query.getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> chunkIds = new ArrayList<>();
        Map<String, Double> scoreByChunkId = new LinkedHashMap<>();
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            String chunkId = String.valueOf(values[0]);
            Number score = (Number) values[1];
            chunkIds.add(chunkId);
            scoreByChunkId.put(chunkId, score == null ? 0.0 : score.doubleValue());
        }
        List<DocumentChunkEntity> chunks = entityManager.createQuery(
                        "select c from DocumentChunkEntity c where c.chunkId in :chunkIds", DocumentChunkEntity.class)
                .setParameter("chunkIds", chunkIds)
                .getResultList();
        Map<String, DocumentChunkEntity> chunkById = new LinkedHashMap<>();
        for (DocumentChunkEntity chunk : chunks) {
            chunkById.put(chunk.chunkId, chunk);
        }
        List<KeywordChunkSearchResult> results = new ArrayList<>();
        for (String chunkId : chunkIds) {
            DocumentChunkEntity chunk = chunkById.get(chunkId);
            if (chunk != null) {
                results.add(new KeywordChunkSearchResult(chunk, scoreByChunkId.getOrDefault(chunkId, 0.0)));
            }
        }
        return results;
    }

    private void appendEquals(StringBuilder sql, Map<String, Object> params, String column, String name, String value) {
        if (value != null && !value.isBlank()) {
            sql.append(" AND ").append(column).append(" = :").append(name);
            params.put(name, value);
        }
    }
}
