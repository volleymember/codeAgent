package com.codeagent.memory;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryFeedbackRequest;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import com.codeagent.storage.repository.MemoryEpisodeRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 情节记忆服务。
 *
 * <p>该服务负责管理 Memory Center 中的“情节记忆”，即从历史任务中沉淀出的
 * 问题现象、根因、修复方案、验证方式和可靠性反馈。</p>
 *
 * <p>情节记忆主要用于后续 Agent 任务中的历史经验召回，
 * 帮助 Agent 在遇到相似症状时快速参考过往根因和修复路径。</p>
 *
 * <p>核心能力包括：</p>
 * <ul>
 *     <li>创建或更新情节记忆</li>
 *     <li>根据项目查询记忆列表</li>
 *     <li>根据召回请求匹配相似历史记忆</li>
 *     <li>记录 helpful / unhelpful 反馈</li>
 *     <li>根据反馈动态调整可靠性和状态</li>
 * </ul>
 */
@Service
public class EpisodicMemoryService {

    /**
     * 情节记忆仓储。
     */
    private final MemoryEpisodeRepository repository;

    /**
     * 情节记忆评分器。
     *
     * <p>用于计算召回请求和历史记忆之间的相似度分数，
     * 并生成匹配原因。</p>
     */
    private final EpisodicMemoryScorer scorer;

    /**
     * Memory Center 配置。
     *
     * <p>包含全局项目标识、最小召回分数等配置。</p>
     */
    private final MemoryProperties properties;

    /**
     * 创建情节记忆服务。
     *
     * @param repository 情节记忆仓储
     * @param scorer     情节记忆评分器
     * @param properties Memory Center 配置
     */
    public EpisodicMemoryService(MemoryEpisodeRepository repository,
                                 EpisodicMemoryScorer scorer,
                                 MemoryProperties properties) {
        this.repository = repository;
        this.scorer = scorer;
        this.properties = properties;
    }

    /**
     * 创建情节记忆。
     *
     * <p>这是兼容旧调用方式的便捷方法，不额外传入 tags 和 confidenceScore。
     * 实际创建逻辑会委托给完整参数版本。</p>
     *
     * @param projectKey  所属项目标识
     * @param symptoms    问题症状列表
     * @param rootCause   根因描述
     * @param fix         修复方案
     * @param verifiedBy  验证方式或验证证据
     * @param sourceUri   来源 URI
     * @param reliability 可靠性标记，例如 candidate、verified、low_confidence
     * @return 创建或更新后的情节记忆实体
     */
    public MemoryEpisodeEntity create(String projectKey, List<String> symptoms, String rootCause,
                                      String fix, String verifiedBy, String sourceUri, String reliability) {
        return create(projectKey, symptoms, rootCause, fix, verifiedBy, sourceUri, reliability, List.of(), null);
    }

    /**
     * 创建或更新情节记忆。
     *
     * <p>该方法会先根据 projectKey 和 symptoms 计算 symptomSignature。
     * 如果同一项目下已存在相同症状签名的记忆，则更新最新一条；
     * 否则创建新的情节记忆。</p>
     *
     * @param projectKey       所属项目标识
     * @param symptoms         问题症状列表
     * @param rootCause        根因描述
     * @param fix              修复方案
     * @param verifiedBy       验证方式或验证证据
     * @param sourceUri        来源 URI
     * @param reliability      可靠性标记；为空时默认为 candidate
     * @param tags             标签列表
     * @param confidenceScore  置信度分数，可为空；为空时保留原值
     * @return 创建或更新后的情节记忆实体
     */
    public MemoryEpisodeEntity create(String projectKey,
                                      List<String> symptoms,
                                      String rootCause,
                                      String fix,
                                      String verifiedBy,
                                      String sourceUri,
                                      String reliability,
                                      List<String> tags,
                                      Double confidenceScore) {
        validate(projectKey, symptoms, rootCause, fix);

        String signature = signature(projectKey, symptoms);

        // 如果同一项目下已存在相同症状签名，则复用并更新该记忆，避免重复沉淀
        MemoryEpisodeEntity entity = repository.findFirstByProjectKeyAndSymptomSignatureOrderByIdDesc(projectKey, signature)
                .orElseGet(MemoryEpisodeEntity::new);

        boolean existing = entity.id != null;

        if (!existing) {
            // 新建记忆时初始化基础字段和反馈统计
            entity.episodeId = "EP-" + UUID.randomUUID();
            entity.createdAt = LocalDateTime.now();
            entity.hitCount = 0;
            entity.helpfulCount = 0;
            entity.unhelpfulCount = 0;
            entity.status = "ACTIVE";
        }

        entity.projectKey = projectKey;
        entity.symptomsJson = JsonSupport.toJson(symptoms == null ? List.of() : symptoms);
        entity.symptomSignature = signature;
        entity.rootCause = rootCause;
        entity.fixContent = fix;
        entity.verifiedBy = verifiedBy;
        entity.sourceUri = sourceUri;
        entity.reliability = reliability == null ? "candidate" : reliability;
        entity.tagsJson = JsonSupport.toJson(tags == null ? List.of() : tags);

        // confidenceScore 为空时保留原有置信度，避免更新时误清空
        entity.confidenceScore = confidenceScore == null ? entity.confidenceScore : confidenceScore;

        // 状态为空时默认激活
        entity.status = entity.status == null || entity.status.isBlank() ? "ACTIVE" : entity.status;
        entity.updatedAt = LocalDateTime.now();

        return repository.save(entity);
    }

    /**
     * 查询指定项目下的情节记忆。
     *
     * @param projectKey 项目标识
     * @return 按 ID 倒序排列的情节记忆列表
     */
    public List<MemoryEpisodeEntity> list(String projectKey) {
        return repository.findByProjectKeyOrderByIdDesc(projectKey);
    }

    /**
     * 根据请求召回相似情节记忆。
     *
     * <p>召回范围包括全局项目和当前项目。查询出的候选记忆会经过以下处理：</p>
     * <ol>
     *     <li>只保留 ACTIVE 状态或状态为空的记忆</li>
     *     <li>使用 EpisodicMemoryScorer 计算匹配分数</li>
     *     <li>过滤低于最小召回分数的结果</li>
     *     <li>按分数倒序排序</li>
     *     <li>限制返回数量</li>
     *     <li>更新命中次数和最后召回时间</li>
     * </ol>
     *
     * @param request 记忆召回请求
     * @return 匹配到的情节记忆列表
     * @throws BusinessException 当 projectKey 为空时抛出
     */
    public List<MemoryEpisodeMatch> recall(MemoryRecallRequest request) {
        if (request.projectKey() == null || request.projectKey().isBlank()) {
            throw new BusinessException("MEMORY_PROJECT_KEY_REQUIRED", "Memory recall projectKey must not be blank.");
        }

        // 同时召回全局项目和当前项目下的历史经验
        List<String> projectKeys = List.of(properties.getGlobalProjectKey(), request.projectKey());

        List<MemoryEpisodeMatch> matches = repository.findTop100ByProjectKeyInOrderByUpdatedAtDesc(projectKeys).stream()
                .filter(episode -> episode.status == null || "ACTIVE".equalsIgnoreCase(episode.status))
                .map(episode -> toMatch(request, episode))
                .filter(match -> match.score() >= Math.max(0.0, properties.getMinRecallScore()))
                .sorted(Comparator.comparingDouble(MemoryEpisodeMatch::score).reversed())
                .limit(Math.max(1, request.limit()))
                .toList();

        // 召回后更新命中统计
        markRecalled(matches);

        return matches;
    }

    /**
     * 记录情节记忆反馈。
     *
     * <p>当反馈为 helpful 时，会增加 helpfulCount 并提升置信度；
     * 当反馈为 unhelpful 时，会增加 unhelpfulCount 并降低置信度。
     * 随后会根据反馈统计重新计算 reliability 和 status。</p>
     *
     * @param episodeId 情节记忆 ID
     * @param request   反馈请求
     * @return 更新后的情节记忆实体
     * @throws BusinessException 当 episodeId 为空或记忆不存在时抛出
     */
    public MemoryEpisodeEntity feedback(String episodeId, MemoryFeedbackRequest request) {
        if (episodeId == null || episodeId.isBlank()) {
            throw new BusinessException("MEMORY_EPISODE_ID_REQUIRED", "Memory episodeId must not be blank.");
        }

        MemoryEpisodeEntity entity = repository.findByEpisodeId(episodeId)
                .orElseThrow(() -> new BusinessException("MEMORY_EPISODE_NOT_FOUND", "Memory episode not found: " + episodeId));

        if (request.helpful()) {
            entity.helpfulCount = entity.helpfulCount == null ? 1 : entity.helpfulCount + 1;
            entity.confidenceScore = clamp((entity.confidenceScore == null ? 0.5 : entity.confidenceScore) + 0.08);
        } else {
            entity.unhelpfulCount = entity.unhelpfulCount == null ? 1 : entity.unhelpfulCount + 1;
            entity.confidenceScore = clamp((entity.confidenceScore == null ? 0.5 : entity.confidenceScore) - 0.12);
        }

        entity.reliability = reliability(entity);
        entity.status = status(entity);
        entity.feedbackJson = appendFeedback(entity.feedbackJson, request);
        entity.updatedAt = LocalDateTime.now();

        return repository.save(entity);
    }

    /**
     * 将记忆实体转换为召回匹配结果。
     *
     * @param request 召回请求
     * @param episode 情节记忆实体
     * @return 召回匹配结果
     */
    private MemoryEpisodeMatch toMatch(MemoryRecallRequest request, MemoryEpisodeEntity episode) {
        double score = scorer.score(request, episode);

        return new MemoryEpisodeMatch(
                episode.episodeId,
                episode.projectKey,
                scorer.readSymptoms(episode),
                episode.rootCause,
                episode.fixContent,
                episode.verifiedBy,
                episode.sourceUri,
                episode.reliability,
                score,
                scorer.reason(request, episode, score)
        );
    }

    /**
     * 标记记忆已被召回。
     *
     * <p>该方法会根据匹配结果反查对应情节记忆，
     * 并更新 hitCount、lastRecalledAt 和 updatedAt。</p>
     *
     * @param matches 本次召回匹配结果
     */
    private void markRecalled(List<MemoryEpisodeMatch> matches) {
        for (MemoryEpisodeMatch match : matches) {
            repository.findFirstByProjectKeyAndSymptomSignatureOrderByIdDesc(
                            match.projectKey(), signature(match.projectKey(), match.symptoms()))
                    .ifPresent(entity -> {
                        entity.hitCount = entity.hitCount == null ? 1 : entity.hitCount + 1;
                        entity.lastRecalledAt = LocalDateTime.now();
                        entity.updatedAt = LocalDateTime.now();
                        repository.save(entity);
                    });
        }
    }

    /**
     * 根据反馈统计计算可靠性。
     *
     * <p>规则如下：</p>
     * <ul>
     *     <li>helpful 次数不少于 3，置信度不低于 0.72，且 helpful 多于 unhelpful，则标记为 verified</li>
     *     <li>unhelpful 次数不少于 3，且 unhelpful 多于 helpful，则标记为 low_confidence</li>
     *     <li>其他情况保留原 reliability；为空时默认为 candidate</li>
     * </ul>
     *
     * @param entity 情节记忆实体
     * @return 新的可靠性标记
     */
    private String reliability(MemoryEpisodeEntity entity) {
        int helpful = entity.helpfulCount == null ? 0 : entity.helpfulCount;
        int unhelpful = entity.unhelpfulCount == null ? 0 : entity.unhelpfulCount;
        double confidence = entity.confidenceScore == null ? 0.5 : entity.confidenceScore;

        if (helpful >= 3 && confidence >= 0.72 && helpful > unhelpful) {
            return "verified";
        }

        if (unhelpful >= 3 && unhelpful > helpful) {
            return "low_confidence";
        }

        return entity.reliability == null || entity.reliability.isBlank() ? "candidate" : entity.reliability;
    }

    /**
     * 根据反馈统计计算记忆状态。
     *
     * <p>当 unhelpful 反馈明显较多时，将记忆归档，避免后续继续召回低质量经验。</p>
     *
     * @param entity 情节记忆实体
     * @return ACTIVE 或 ARCHIVED
     */
    private String status(MemoryEpisodeEntity entity) {
        int helpful = entity.helpfulCount == null ? 0 : entity.helpfulCount;
        int unhelpful = entity.unhelpfulCount == null ? 0 : entity.unhelpfulCount;

        if (unhelpful >= 5 && unhelpful >= helpful + 3) {
            return "ARCHIVED";
        }

        return "ACTIVE";
    }

    /**
     * 追加反馈记录。
     *
     * <p>该方法会读取已有 feedbackJson，将本次反馈追加到列表末尾。
     * 如果历史 JSON 解析失败，则清空历史反馈并仅保留本次反馈。
     * 最多保留最近 20 条反馈，避免字段无限增长。</p>
     *
     * @param feedbackJson 历史反馈 JSON
     * @param request      本次反馈请求
     * @return 更新后的反馈 JSON
     */
    private String appendFeedback(String feedbackJson, MemoryFeedbackRequest request) {
        List<Map<String, Object>> feedback = new ArrayList<>();

        if (feedbackJson != null && !feedbackJson.isBlank()) {
            try {
                feedback.addAll(JsonSupport.mapper().readValue(feedbackJson,
                        JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, Map.class)));
            } catch (Exception ignored) {
                // 历史反馈格式异常时不阻断反馈流程，直接重置反馈列表
                feedback.clear();
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("helpful", request.helpful());
        item.put("agentName", request.agentName() == null ? "UNKNOWN" : request.agentName());
        item.put("reason", request.reason() == null ? "" : request.reason());
        item.put("createdAt", LocalDateTime.now().toString());
        feedback.add(item);

        // 只保留最近 20 条反馈，控制存储大小
        if (feedback.size() > 20) {
            feedback = feedback.subList(feedback.size() - 20, feedback.size());
        }

        return JsonSupport.toJson(feedback);
    }

    /**
     * 将置信度限制在 [0, 1] 区间。
     *
     * <p>当输入为 NaN 或无穷大时，返回默认置信度 0.5。</p>
     *
     * @param value 原始置信度
     * @return 规范化后的置信度
     */
    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.5;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * 校验创建情节记忆所需的必填字段。
     *
     * @param projectKey 项目标识
     * @param symptoms   症状列表
     * @param rootCause  根因描述
     * @param fix        修复方案
     * @throws BusinessException 当任一必填字段为空时抛出
     */
    private void validate(String projectKey, List<String> symptoms, String rootCause, String fix) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new BusinessException("MEMORY_PROJECT_KEY_REQUIRED", "Episode memory projectKey must not be blank.");
        }

        if (symptoms == null || symptoms.isEmpty()) {
            throw new BusinessException("MEMORY_SYMPTOMS_REQUIRED", "Episode memory symptoms must not be empty.");
        }

        if (rootCause == null || rootCause.isBlank()) {
            throw new BusinessException("MEMORY_ROOT_CAUSE_REQUIRED", "Episode memory rootCause must not be blank.");
        }

        if (fix == null || fix.isBlank()) {
            throw new BusinessException("MEMORY_FIX_REQUIRED", "Episode memory fix must not be blank.");
        }
    }

    /**
     * 根据项目和症状计算稳定签名。
     *
     * <p>签名使用 projectKey 和 symptoms 拼接后转小写，再计算 SHA-256。
     * 它用于识别同一项目下症状相同或高度一致的记忆，避免重复创建。</p>
     *
     * @param projectKey 项目标识
     * @param symptoms   症状列表
     * @return SHA-256 十六进制签名
     * @throws BusinessException 当签名计算失败时抛出
     */
    private String signature(String projectKey, List<String> symptoms) {
        String joined = (projectKey + "::" + String.join("|", symptoms == null ? List.of() : symptoms)).toLowerCase();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("MEMORY_SIGNATURE_FAILED", "Failed to calculate memory episode signature.", e);
        }
    }
}