package com.codeagent.rag.retrieval;

import java.util.List;
import java.util.Map;

public record RetrievalBranchResult<T>(
        String route,
        List<T> hits,
        long latencyMs,
        Map<String, Object> filterSummary
) {
    public RetrievalBranchResult {
        hits = hits == null ? List.of() : List.copyOf(hits);
        filterSummary = filterSummary == null ? Map.of() : Map.copyOf(filterSummary);
    }
}
