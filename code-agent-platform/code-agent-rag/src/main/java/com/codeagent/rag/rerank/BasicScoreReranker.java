package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BasicScoreReranker {
    public List<RagSearchResult> rerank(List<RagSearchResult> results, int limit) {
        Map<String, RagSearchResult> deduped = new LinkedHashMap<>();
        results.stream()
                .sorted(Comparator.comparingDouble(RagSearchResult::score).reversed())
                .forEach(result -> deduped.putIfAbsent(result.chunkId(), result));
        return deduped.values().stream()
                .limit(Math.max(1, limit))
                .toList();
    }
}
