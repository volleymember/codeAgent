package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 情景记忆评分器。
 *
 * <p>用于计算当前召回请求与某条历史情景记忆之间的相关性得分。
 * 情景记忆通常表示一次历史任务、问题、故障、修复方案或诊断经验。</p>
 *
 * <p>评分主要基于以下因素：</p>
 * <ul>
 *     <li>查询文本与历史记忆文本的词项覆盖率</li>
 *     <li>匹配词项在历史记忆中的精确度</li>
 *     <li>历史记忆的可靠性等级</li>
 *     <li>历史记忆自身的置信度分数</li>
 * </ul>
 */
@Component
public class EpisodicMemoryScorer {
    /**
     * 词项切分正则。
     *
     * <p>保留中文、英文、数字以及部分代码中常见符号，
     * 例如 {@code _ . $ : # -}，以便对类名、方法名、异常名、路径或错误码进行匹配。</p>
     */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsHan}A-Za-z0-9_.$:#\\-]+");

    /**
     * 计算召回请求与历史情景记忆之间的相关性得分。
     *
     * <p>得分范围为 {@code [0.0, 1.0]}。
     * 分数越高，表示该历史记忆与当前请求越相关，越适合作为参考经验被召回。</p>
     *
     * <p>当前评分由以下部分组成：</p>
     * <ul>
     *     <li>coverage：当前请求词项被历史记忆覆盖的比例，占主要权重</li>
     *     <li>precision：匹配词项占历史记忆词项的比例，用于降低过长记忆的泛化误召回</li>
     *     <li>reliability：根据历史记忆可靠性附加的加权分</li>
     *     <li>confidence：根据历史记忆置信度附加的加权分</li>
     * </ul>
     *
     * @param request 当前记忆召回请求
     * @param episode 候选历史情景记忆
     * @return 相关性得分，范围为 {@code [0.0, 1.0]}
     */
    public double score(MemoryRecallRequest request, MemoryEpisodeEntity episode) {
        Set<String> queryTokens = tokens("%s %s %s".formatted(
                request.taskType(), request.query(), String.join(" ", request.symptoms())));
        if (queryTokens.isEmpty()) {
            return 0.0;
        }

        Set<String> episodeTokens = tokens("%s %s %s %s %s".formatted(
                episode.symptomsJson,
                episode.rootCause,
                episode.fixContent,
                episode.tagsJson,
                episode.symptomSignature));
        if (episodeTokens.isEmpty()) {
            return 0.0;
        }

        long overlap = queryTokens.stream().filter(episodeTokens::contains).count();

        // 当前请求中有多少词项被历史记忆覆盖，越高表示历史记忆越能解释当前请求。
        double coverage = (double) overlap / queryTokens.size();

        // 匹配词项占历史记忆词项的比例，用于避免内容很长的记忆因为包含大量词项而被过度召回。
        double precision = (double) overlap / Math.max(1, episodeTokens.size());

        // 根据历史记忆可靠性增加权重，例如 verified / confirmed 会获得更高加分。
        double reliability = reliabilityBoost(episode.reliability);

        // 历史记忆自身置信度加权，限制在 [0, 1] 后最多贡献 0.08 分。
        double confidence = episode.confidenceScore == null ? 0.0 : Math.max(0.0, Math.min(1.0, episode.confidenceScore)) * 0.08;
        double feedback = feedbackBoost(episode);
        double recency = recencyBoost(episode.updatedAt);
        double reuse = reuseBoost(episode.hitCount);

        return clamp(0.58 * coverage + 0.12 * precision + reliability + confidence + feedback + recency + reuse);
    }

    /**
     * 生成评分解释信息。
     *
     * <p>用于调试或日志记录，帮助理解某条情景记忆为什么被召回。
     * 解释内容包括最终得分、匹配到的关键词以及记忆可靠性。</p>
     *
     * @param request 当前记忆召回请求
     * @param episode 候选历史情景记忆
     * @param score   已计算出的相关性得分
     * @return 可读的评分解释文本
     */
    public String reason(MemoryRecallRequest request, MemoryEpisodeEntity episode, double score) {
        Set<String> queryTokens = tokens("%s %s %s".formatted(
                request.taskType(), request.query(), String.join(" ", request.symptoms())));
        Set<String> episodeTokens = tokens("%s %s %s".formatted(episode.symptomsJson, episode.rootCause, episode.fixContent));

        List<String> matched = queryTokens.stream()
                .filter(episodeTokens::contains)
                .limit(8)
                .toList();

        return "score=%.4f matchedTerms=%s reliability=%s".formatted(score, matched, episode.reliability);
    }

    /**
     * 读取情景记忆中的症状列表。
     *
     * <p>症状以 JSON 数组格式存储在 {@code symptomsJson} 字段中。
     * 当字段为空或解析失败时，返回空列表，避免异常影响召回流程。</p>
     *
     * @param episode 历史情景记忆实体
     * @return 症状列表
     */
    public List<String> readSymptoms(MemoryEpisodeEntity episode) {
        if (episode.symptomsJson == null || episode.symptomsJson.isBlank()) {
            return List.of();
        }
        try {
            return JsonSupport.mapper().readValue(episode.symptomsJson,
                    JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 将文本切分为去重后的词项集合。
     *
     * <p>该方法会统一转为小写、去除空白词项、过滤长度小于 2 的词项，
     * 并最多保留前 128 个词项，避免过长文本影响性能。</p>
     *
     * <p>使用 {@link LinkedHashSet} 是为了在去重的同时保留词项出现顺序，
     * 便于后续生成可读的匹配原因。</p>
     *
     * @param value 原始文本
     * @return 去重后的词项集合
     */
    Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(TOKEN_SPLIT.split(value.toLowerCase(Locale.ROOT)))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .limit(128)
                .forEach(tokens::add);
        return tokens;
    }

    /**
     * 根据历史记忆的可靠性等级计算加权分。
     *
     * <p>可靠性越高，召回得分越容易提高。
     * 例如 verified / confirmed 表示经过验证或确认的经验，
     * candidate 表示候选经验，low 表示低可靠性经验。</p>
     *
     * @param reliability 可靠性描述
     * @return 可靠性加权分
     */
    private double reliabilityBoost(String reliability) {
        if (reliability == null) {
            return 0.02;
        }
        String normalized = reliability.toLowerCase(Locale.ROOT);
        if (normalized.contains("verified") || normalized.contains("confirmed")) {
            return 0.16;
        }
        if (normalized.contains("candidate")) {
            return 0.08;
        }
        if (normalized.contains("low")) {
            return 0.02;
        }
        return 0.05;
    }

    private double feedbackBoost(MemoryEpisodeEntity episode) {
        int helpful = episode.helpfulCount == null ? 0 : episode.helpfulCount;
        int unhelpful = episode.unhelpfulCount == null ? 0 : episode.unhelpfulCount;
        int total = helpful + unhelpful;
        if (total == 0) {
            return 0.0;
        }
        double helpfulRatio = (double) helpful / total;
        return Math.max(-0.08, Math.min(0.08, (helpfulRatio - 0.5) * 0.16));
    }

    private double recencyBoost(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            return 0.0;
        }
        long days = Math.max(0, Duration.between(updatedAt, LocalDateTime.now()).toDays());
        if (days <= 7) {
            return 0.04;
        }
        if (days <= 30) {
            return 0.025;
        }
        if (days <= 90) {
            return 0.01;
        }
        return 0.0;
    }

    private double reuseBoost(Integer hitCount) {
        int hits = hitCount == null ? 0 : hitCount;
        if (hits <= 0) {
            return 0.0;
        }
        return Math.min(0.04, Math.log10(hits + 1) * 0.025);
    }

    /**
     * 将分数裁剪到合法范围。
     *
     * <p>当分数为 NaN 或无穷大时返回 {@code 0.0}；
     * 其他情况下将分数限制在 {@code [0.0, 1.0]} 范围内。</p>
     *
     * @param value 原始分数
     * @return 裁剪后的分数
     */
    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
