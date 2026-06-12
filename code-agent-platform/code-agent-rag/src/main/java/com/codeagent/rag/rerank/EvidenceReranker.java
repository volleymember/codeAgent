package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EvidenceReranker {
    private final SourcePriorityScorer sourcePriorityScorer;
    private final SymbolMatchScorer symbolMatchScorer;
    private final LocationMatchScorer locationMatchScorer;
    private final FreshnessScorer freshnessScorer;
    private final FeedbackScorer feedbackScorer;

    public EvidenceReranker(SourcePriorityScorer sourcePriorityScorer,
                            SymbolMatchScorer symbolMatchScorer,
                            LocationMatchScorer locationMatchScorer,
                            FreshnessScorer freshnessScorer,
                            FeedbackScorer feedbackScorer) {
        this.sourcePriorityScorer = sourcePriorityScorer;
        this.symbolMatchScorer = symbolMatchScorer;
        this.locationMatchScorer = locationMatchScorer;
        this.freshnessScorer = freshnessScorer;
        this.feedbackScorer = feedbackScorer;
    }

    public List<RagSearchResult> rerank(RagSearchRequest request, List<RagSearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Double> denseNormalized = normalize(candidates.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.chunkId(), item.denseScore()), Map::putAll), true);
        Map<String, Double> keywordNormalized = normalize(candidates.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.chunkId(), item.keywordScore()), Map::putAll), false);
        List<RagSearchResult> reranked = candidates.stream()
                .map(candidate -> applyScores(request, candidate,
                        denseNormalized.getOrDefault(candidate.chunkId(), 0.0),
                        keywordNormalized.getOrDefault(candidate.chunkId(), 0.0)))
                .sorted(Comparator.comparingDouble(RagSearchResult::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
        log.info("Evidence rerank completed candidates={} topK={} returned={}",
                candidates.size(), topK, reranked.size());
        return reranked;
    }

    private RagSearchResult applyScores(RagSearchRequest request,
                                        RagSearchResult candidate,
                                        double denseScore,
                                        double keywordScore) {
        double symbolMatchScore = symbolMatchScorer.score(request, candidate);
        double sourcePriorityScore = sourcePriorityScorer.score(request, candidate);
        double freshnessScore = freshnessScorer.score(request, candidate);
        double locationScore = locationMatchScorer.score(request, candidate);
        double feedbackScore = feedbackScorer.score(request, candidate);
        // TODO: move weights to RagProperties when product tuning starts.
        double finalScore = clamp(
                0.30 * denseScore
                        + 0.25 * keywordScore
                        + 0.15 * symbolMatchScore
                        + 0.10 * sourcePriorityScore
                        + 0.10 * freshnessScore
                        + 0.05 * locationScore
                        + 0.05 * feedbackScore
        );
        RerankScoreBreakdown breakdown = new RerankScoreBreakdown(finalScore, denseScore, keywordScore,
                symbolMatchScore, sourcePriorityScore, freshnessScore, locationScore, feedbackScore);
        return new RagSearchResult(
                candidate.chunkId(),
                candidate.evidenceId(),
                candidate.sourceSystem(),
                candidate.evidenceType(),
                candidate.title(),
                candidate.content(),
                candidate.sourceUri(),
                candidate.sourceUrl(),
                candidate.filePath(),
                candidate.lineRange(),
                candidate.symbolName(),
                candidate.keywords(),
                candidate.lineStart(),
                candidate.lineEnd(),
                candidate.createdAt(),
                finalScore,
                denseScore,
                keywordScore,
                feedbackScore,
                matchReason(breakdown)
        );
    }

    private String matchReason(RerankScoreBreakdown score) {
        StringBuilder reason = new StringBuilder();
        if (score.denseScore() >= 0.65) {
            reason.append("向量语义匹配");
        }
        if (score.keywordScore() >= 0.55) {
            append(reason, "关键词命中");
        }
        if (score.symbolMatchScore() >= 0.55) {
            append(reason, "符号/异常关键字匹配");
        }
        if (score.sourcePriorityScore() >= 0.75) {
            append(reason, "高优先级数据源");
        }
        if (score.freshnessScore() >= 0.65) {
            append(reason, "数据新鲜");
        }
        if (score.locationScore() >= 0.75) {
            append(reason, "代码位置相关");
        }
        if (score.feedbackScore() > 0.5) {
            append(reason, "人工反馈加权");
        }
        if (reason.isEmpty()) {
            reason.append("综合相关性匹配");
        }
        reason.append(" finalScore=").append(format(score.finalScore()))
                .append(", dense=").append(format(score.denseScore()))
                .append(", keyword=").append(format(score.keywordScore()))
                .append(", symbol=").append(format(score.symbolMatchScore()))
                .append(", source=").append(format(score.sourcePriorityScore()))
                .append(", freshness=").append(format(score.freshnessScore()))
                .append(", location=").append(format(score.locationScore()))
                .append(", feedback=").append(format(score.feedbackScore()));
        return reason.toString();
    }

    private void append(StringBuilder builder, String value) {
        if (!builder.isEmpty()) {
            builder.append("；");
        }
        builder.append(value);
    }

    private Map<String, Double> normalize(Map<String, Double> rawScores, boolean dense) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        if (rawScores.isEmpty()) {
            return normalized;
        }
        if (dense) {
            boolean unitRange = rawScores.values().stream().allMatch(score -> score >= 0.0 && score <= 1.0);
            boolean cosineRange = rawScores.values().stream().allMatch(score -> score >= -1.0 && score <= 1.0);
            for (Map.Entry<String, Double> entry : rawScores.entrySet()) {
                double score = entry.getValue() == null ? 0.0 : entry.getValue();
                normalized.put(entry.getKey(), unitRange ? clamp(score) : cosineRange ? clamp((score + 1.0) / 2.0) : 0.0);
            }
            return unitRange || cosineRange ? normalized : minMaxNormalize(rawScores);
        }
        return minMaxNormalize(rawScores);
    }

    private Map<String, Double> minMaxNormalize(Map<String, Double> rawScores) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        double min = rawScores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = rawScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (Double.compare(min, max) == 0) {
            double value = max > 0.0 ? 1.0 : 0.0;
            rawScores.keySet().forEach(key -> normalized.put(key, value));
            return normalized;
        }
        rawScores.forEach((key, value) -> normalized.put(key, clamp((value - min) / (max - min))));
        return normalized;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String format(double value) {
        return "%.4f".formatted(value);
    }
}
