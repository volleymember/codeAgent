package com.codeagent.rag.embedding;

import java.util.List;

public interface EmbeddingClient {
    List<Double> embed(String input);
}
