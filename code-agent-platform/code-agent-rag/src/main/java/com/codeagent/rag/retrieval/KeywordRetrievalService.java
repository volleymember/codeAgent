package com.codeagent.rag.retrieval;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.storage.repository.DocumentChunkRepository;
import com.codeagent.storage.repository.KeywordChunkSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KeywordRetrievalService {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^A-Za-z0-9_.$:#\\-]+");
    private final DocumentChunkRepository documentChunkRepository;

    public KeywordRetrievalService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    public RetrievalBranchResult<KeywordRetrievalHit> search(RagSearchRequest request, MetadataFilter filter) {
        long startedAt = System.currentTimeMillis();
        if (!filter.keywordEnabled()) {
            return new RetrievalBranchResult<>("keyword", List.of(), elapsed(startedAt), filter.summary());
        }
        String queryText = request.query().trim();
        String booleanQuery = booleanQuery(queryText);
        String likeQuery = "%" + queryText.toLowerCase(Locale.ROOT) + "%";
        List<KeywordChunkSearchResult> rows = documentChunkRepository.searchByKeyword(
                queryText,
                booleanQuery,
                likeQuery,
                filter.projectKey(),
                filter.moduleName(),
                filter.branch(),
                filter.commitId(),
                filter.buildId(),
                filter.evidenceTypes(),
                filter.createdFrom(),
                filter.createdTo(),
                filter.keywordLimit());
        List<KeywordRetrievalHit> hits = rows.stream()
                .map(row -> new KeywordRetrievalHit(row.chunk(), row.keywordScore()))
                .toList();
        Map<String, Object> logFilter = new LinkedHashMap<>(filter.summary());
        logFilter.put("booleanQuery", booleanQuery);
        log.info("Keyword retrieval completed hits={} latencyMs={} filter={}",
                hits.size(), elapsed(startedAt), logFilter);
        return new RetrievalBranchResult<>("keyword", hits, elapsed(startedAt), logFilter);
    }

    private String booleanQuery(String query) {
        List<String> tokens = Arrays.stream(TOKEN_SPLIT.split(query))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(12)
                .toList();
        if (tokens.isEmpty()) {
            return query;
        }
        return tokens.stream()
                .map(token -> "+" + token + "*")
                .reduce((left, right) -> left + " " + right)
                .orElse(query);
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
