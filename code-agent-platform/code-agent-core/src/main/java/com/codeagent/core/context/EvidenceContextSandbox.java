package com.codeagent.core.context;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.security.SensitiveDataMasker;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.llm.config.LlmProperties;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceContextSandbox {
    private static final List<String> METADATA_ALLOW_LIST = List.of(
            "chunkId", "evidenceId", "denseScore", "keywordScore", "feedbackScore",
            "status", "metric", "value", "failCount", "changedFiles", "issueTotal",
            "sandbox"
    );
    private final LlmProperties llmProperties;

    public EvidenceContextSandbox(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    public ManagedEvidenceContext build(List<EvidenceItem> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return new ManagedEvidenceContext(0, 0, 0, 0,
                    llmProperties.getMaxEvidenceTokens(), "EMPTY", List.of());
        }
        int maxEvidenceTokens = Math.max(1, llmProperties.getMaxEvidenceTokens());
        List<EvidenceItem> ranked = evidence.stream()
                .sorted(Comparator.comparingDouble(EvidenceItem::score).reversed())
                .toList();
        List<Map<String, Object>> included = new java.util.ArrayList<>();
        int estimated = 0;
        for (EvidenceItem item : ranked) {
            Map<String, Object> candidate = toContextItem(item);
            int candidateTokens = TokenEstimator.estimate(JsonSupport.toJson(candidate), llmProperties.getCharsPerToken());
            if (!included.isEmpty() && estimated + candidateTokens > maxEvidenceTokens) {
                continue;
            }
            if (candidateTokens > maxEvidenceTokens) {
                candidate = shrink(candidate, maxEvidenceTokens);
                candidateTokens = TokenEstimator.estimate(JsonSupport.toJson(candidate), llmProperties.getCharsPerToken());
            }
            included.add(candidate);
            estimated += candidateTokens;
            if (estimated >= maxEvidenceTokens) {
                break;
            }
        }
        int omitted = Math.max(0, evidence.size() - included.size());
        return new ManagedEvidenceContext(evidence.size(), included.size(), omitted, estimated,
                maxEvidenceTokens, omitted > 0 ? "TOP_SCORE_TRUNCATED" : "PASS", included);
    }

    private Map<String, Object> toContextItem(EvidenceItem item) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sourceType", value(item.sourceType()));
        context.put("sourceSystem", value(item.sourceSystem()));
        context.put("title", value(item.title()));
        context.put("score", item.score());
        context.put("matchReason", value(item.matchReason()));
        context.put("summary", TokenEstimator.truncateByTokens(
                SensitiveDataMasker.mask(value(item.summary())), 320, llmProperties.getCharsPerToken()));
        context.put("sourceUrl", value(item.sourceUrl()));
        context.put("filePath", value(item.filePath()));
        context.put("lineRange", value(item.lineRange()));
        context.put("rawRef", value(item.rawRef()));
        context.put("metadata", allowedMetadata(item.metadata()));
        return context;
    }

    private Map<String, Object> allowedMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> allowed = new LinkedHashMap<>();
        for (String key : METADATA_ALLOW_LIST) {
            if (!metadata.containsKey(key)) {
                continue;
            }
            Object value = metadata.get(key);
            if (value == null) {
                continue;
            }
            allowed.put(key, sanitizeMetadataValue(value));
        }
        return allowed;
    }

    private Object sanitizeMetadataValue(Object value) {
        if (value instanceof String text) {
            return TokenEstimator.truncateByTokens(SensitiveDataMasker.mask(text), 120, llmProperties.getCharsPerToken());
        }
        String json = JsonSupport.toJson(value);
        if (TokenEstimator.estimate(json, llmProperties.getCharsPerToken()) > 240) {
            return TokenEstimator.truncateByTokens(SensitiveDataMasker.mask(json), 240, llmProperties.getCharsPerToken());
        }
        return value;
    }

    private Map<String, Object> shrink(Map<String, Object> candidate, int maxEvidenceTokens) {
        Map<String, Object> shrunk = new LinkedHashMap<>(candidate);
        shrunk.put("summary", TokenEstimator.truncateByTokens(
                String.valueOf(candidate.get("summary")), Math.max(80, maxEvidenceTokens / 2), llmProperties.getCharsPerToken()));
        shrunk.put("metadata", Map.of("truncated", true));
        return shrunk;
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
