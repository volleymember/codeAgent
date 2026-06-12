package com.codeagent.rag.index;

import com.codeagent.rag.config.MilvusProperties;
import com.codeagent.rag.embedding.EmbeddingService;
import com.codeagent.rag.model.EvidenceChunk;
import com.codeagent.rag.vector.MilvusVectorStore;
import com.codeagent.rag.vector.VectorStore;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceVectorIndexer {
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final MilvusProperties milvusProperties;

    public EvidenceVectorIndexer(EmbeddingService embeddingService,
                                 VectorStore vectorStore,
                                 MilvusProperties milvusProperties) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.milvusProperties = milvusProperties;
    }

    public void index(EvidenceChunk chunk) {
        List<Double> vector = embeddingService.embed(chunk);
        vectorStore.upsert(milvusProperties.getCollection(), chunk.getVectorId(), vector, milvusMetadata(chunk));
    }

    public void delete(String vectorId) {
        vectorStore.delete(milvusProperties.getCollection(), vectorId);
    }

    private Map<String, Object> milvusMetadata(EvidenceChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(MilvusVectorStore.CHUNK_ID_FIELD, chunk.getChunkId());
        return metadata;
    }
}
