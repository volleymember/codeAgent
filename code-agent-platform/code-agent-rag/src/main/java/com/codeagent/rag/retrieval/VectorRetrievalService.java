package com.codeagent.rag.retrieval;

import com.codeagent.rag.config.MilvusProperties;
import com.codeagent.rag.embedding.EmbeddingService;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.vector.MilvusVectorStore;
import com.codeagent.rag.vector.VectorSearchHit;
import com.codeagent.rag.vector.VectorStore;
import com.codeagent.storage.entity.DocumentChunkEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VectorRetrievalService {
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final MilvusProperties milvusProperties;

    public VectorRetrievalService(EmbeddingService embeddingService,
                                  VectorStore vectorStore,
                                  MilvusProperties milvusProperties) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.milvusProperties = milvusProperties;
    }

    public RetrievalBranchResult<DenseRetrievalHit> search(RagSearchRequest request,
                                                           MetadataFilter filter,
                                                           List<DocumentChunkEntity> candidates) {
        long startedAt = System.currentTimeMillis();
        if (!filter.vectorEnabled() || candidates.isEmpty()) {
            return new RetrievalBranchResult<>("vector", List.of(), elapsed(startedAt), filter.summary());
        }
        Map<String, DocumentChunkEntity> chunkByVectorId = candidates.stream()
                .filter(chunk -> chunk.vectorId != null && !chunk.vectorId.isBlank())
                .collect(Collectors.toMap(chunk -> chunk.vectorId, Function.identity(), (left, right) -> left));
        if (chunkByVectorId.isEmpty()) {
            return new RetrievalBranchResult<>("vector", List.of(), elapsed(startedAt), filter.summary());
        }
        List<Double> queryVector = embeddingService.embed(request.query());
        Map<String, Object> vectorFilter = Map.of(
                MilvusVectorStore.CHUNK_ID_FIELD,
                candidates.stream().map(chunk -> chunk.chunkId).toList()
        );
        List<VectorSearchHit> hits = vectorStore.search(
                milvusProperties.getCollection(),
                queryVector,
                filter.vectorLimit(),
                vectorFilter);
        List<DenseRetrievalHit> denseHits = hits.stream()
                .map(hit -> new DenseRetrievalHit(chunkByVectorId.get(hit.id()), hit.score()))
                .filter(hit -> hit.chunk() != null)
                .toList();
        Map<String, Object> logFilter = new LinkedHashMap<>(filter.summary());
        logFilter.put("candidateChunks", candidates.size());
        logFilter.put("candidateVectors", chunkByVectorId.size());
        logFilter.put("milvusCollection", milvusProperties.getCollection());
        log.info("Vector retrieval completed hits={} latencyMs={} filter={}",
                denseHits.size(), elapsed(startedAt), logFilter);
        return new RetrievalBranchResult<>("vector", denseHits, elapsed(startedAt), logFilter);
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
