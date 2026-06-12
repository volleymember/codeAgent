package com.codeagent.rag.retrieval;

import com.codeagent.rag.rerank.EvidenceReranker;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import com.codeagent.rag.store.EvidenceStore;
import com.codeagent.storage.entity.DocumentChunkEntity;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class HybridRetrievalService {
    private final MetadataFilterBuilder metadataFilterBuilder;
    private final EvidenceStore evidenceStore;
    private final VectorRetrievalService vectorRetrievalService;
    private final KeywordRetrievalService keywordRetrievalService;
    private final EvidenceReranker evidenceReranker;
    private final ExecutorService retrievalExecutor = Executors.newFixedThreadPool(2);

    public HybridRetrievalService(MetadataFilterBuilder metadataFilterBuilder,
                                  EvidenceStore evidenceStore,
                                  VectorRetrievalService vectorRetrievalService,
                                  KeywordRetrievalService keywordRetrievalService,
                                  EvidenceReranker evidenceReranker) {
        this.metadataFilterBuilder = metadataFilterBuilder;
        this.evidenceStore = evidenceStore;
        this.vectorRetrievalService = vectorRetrievalService;
        this.keywordRetrievalService = keywordRetrievalService;
        this.evidenceReranker = evidenceReranker;
    }

    public List<RagSearchResult> search(RagSearchRequest request) {
        long startedAt = System.currentTimeMillis();
        MetadataFilter filter = metadataFilterBuilder.build(request);
        List<DocumentChunkEntity> candidates = evidenceStore.findCandidateChunks(filter.specification());
        if (candidates.isEmpty()) {
            evidenceStore.saveRetrievalLog(request, 0, elapsed(startedAt));
            log.info("Hybrid retrieval skipped because metadata filter returned no candidates filter={}", filter.summary());
            return List.of();
        }
        CompletableFuture<RetrievalBranchResult<DenseRetrievalHit>> vectorFuture = CompletableFuture.supplyAsync(
                () -> vectorRetrievalService.search(request, filter, candidates), retrievalExecutor)
                .exceptionally(error -> {
                    log.error("Vector retrieval branch failed filter={}", filter.summary(), error);
                    return new RetrievalBranchResult<>("vector", List.of(), 0, filter.summary());
                });
        CompletableFuture<RetrievalBranchResult<KeywordRetrievalHit>> keywordFuture = CompletableFuture.supplyAsync(
                () -> keywordRetrievalService.search(request, filter), retrievalExecutor)
                .exceptionally(error -> {
                    log.error("Keyword retrieval branch failed filter={}", filter.summary(), error);
                    return new RetrievalBranchResult<>("keyword", List.of(), 0, filter.summary());
        });
        RetrievalBranchResult<DenseRetrievalHit> denseResult = vectorFuture.join();
        RetrievalBranchResult<KeywordRetrievalHit> keywordResult = keywordFuture.join();
        List<RagSearchResult> merged = merge(denseResult.hits(), keywordResult.hits());
        List<RagSearchResult> results = evidenceReranker.rerank(request, merged, filter.finalLimit());
        evidenceStore.saveRetrievalLog(request, results.size(), elapsed(startedAt));
        log.info("Hybrid retrieval completed candidates={} vectorHits={} vectorLatencyMs={} keywordHits={} keywordLatencyMs={} results={} latencyMs={} filter={}",
                candidates.size(), denseResult.hits().size(), denseResult.latencyMs(),
                keywordResult.hits().size(), keywordResult.latencyMs(), results.size(),
                elapsed(startedAt), filter.summary());
        return results;
    }

    @PreDestroy
    public void shutdown() {
        retrievalExecutor.shutdownNow();
    }

    private List<RagSearchResult> merge(List<DenseRetrievalHit> denseHits,
                                        List<KeywordRetrievalHit> keywordHits) {
        Map<String, CombinedHit> combined = new LinkedHashMap<>();
        for (DenseRetrievalHit hit : denseHits) {
            if (hit.chunk() == null || hit.chunk().chunkId == null) {
                continue;
            }
            CombinedHit value = combined.computeIfAbsent(hit.chunk().chunkId, key -> new CombinedHit(hit.chunk()));
            value.denseScore = hit.denseScore();
            value.hasDense = true;
        }
        for (KeywordRetrievalHit hit : keywordHits) {
            if (hit.chunk() == null || hit.chunk().chunkId == null) {
                continue;
            }
            CombinedHit value = combined.computeIfAbsent(hit.chunk().chunkId, key -> new CombinedHit(hit.chunk()));
            value.keywordScore = hit.keywordScore();
            value.hasKeyword = true;
        }
        return combined.values().stream().map(this::toResult).toList();
    }

    private RagSearchResult toResult(CombinedHit hit) {
        DocumentChunkEntity chunk = hit.chunk;
        return new RagSearchResult(
                value(chunk.chunkId),
                value(chunk.evidenceNo),
                value(chunk.sourceSystem),
                value(chunk.evidenceType),
                value(chunk.title),
                value(chunk.content),
                value(chunk.sourceUri),
                value(chunk.sourceUrl, chunk.sourceUri),
                value(chunk.filePath),
                value(chunk.lineRange),
                value(chunk.symbolName),
                value(chunk.keywords),
                chunk.lineStart,
                chunk.lineEnd,
                chunk.createdAt,
                0.0,
                hit.hasDense ? hit.denseScore : 0.0,
                hit.hasKeyword ? hit.keywordScore : 0.0,
                0.5,
                matchReason(hit)
        );
    }

    private String matchReason(CombinedHit hit) {
        if (hit.hasDense && hit.hasKeyword) {
            return "vector+keyword denseScore=%s keywordScore=%s".formatted(hit.denseScore, hit.keywordScore);
        }
        if (hit.hasDense) {
            return "vector denseScore=%s".formatted(hit.denseScore);
        }
        if (hit.hasKeyword) {
            return "keyword keywordScore=%s".formatted(hit.keywordScore);
        }
        return "metadata";
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? value(fallback) : value;
    }

    private static class CombinedHit {
        private final DocumentChunkEntity chunk;
        private boolean hasDense;
        private boolean hasKeyword;
        private double denseScore;
        private double keywordScore;

        private CombinedHit(DocumentChunkEntity chunk) {
            this.chunk = chunk;
        }
    }
}
