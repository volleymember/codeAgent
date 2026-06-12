package com.codeagent.rag.embedding;

import com.codeagent.rag.model.EvidenceChunk;

import java.util.List;

public interface EmbeddingService {
    List<Double> embed(String text);

    List<Double> embed(EvidenceChunk chunk);
}
