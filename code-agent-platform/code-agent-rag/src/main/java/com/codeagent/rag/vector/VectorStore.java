package com.codeagent.rag.vector;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(String collection, String id, List<Double> vector, Map<String, Object> metadata);

    List<VectorSearchHit> search(String collection, List<Double> queryVector, int topK, Map<String, Object> filter);

    void delete(String collection, String id);
}
