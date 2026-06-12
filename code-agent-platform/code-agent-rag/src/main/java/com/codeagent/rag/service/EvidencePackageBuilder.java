package com.codeagent.rag.service;

import com.codeagent.common.domain.Citation;
import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.domain.EvidencePackage;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EvidencePackageBuilder {
    public EvidencePackage build(RagSearchRequest request, List<RagSearchResult> results) {
        List<EvidenceItem> items = results.stream()
                .map(result -> new EvidenceItem(
                        result.evidenceType(),
                        result.sourceSystem(),
                        result.title(),
                        summarize(result.content()),
                        result.score(),
                        result.sourceUri(),
                        result.sourceUrl(),
                        result.filePath(),
                        result.lineRange(),
                        result.evidenceId(),
                        result.matchReason(),
                        Map.of(
                                "chunkId", result.chunkId(),
                                "evidenceId", result.evidenceId(),
                                "score", result.score(),
                                "denseScore", result.denseScore(),
                                "keywordScore", result.keywordScore(),
                                "feedbackScore", result.feedbackScore(),
                                "matchReason", result.matchReason()
                        )))
                .toList();
        List<Citation> citations = results.stream()
                .map(result -> new Citation(result.sourceSystem(), result.sourceUrl(), result.filePath(), result.lineRange()))
                .toList();
        return new EvidencePackage(request.projectKey(), request.query(), items.size(), items, citations);
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
