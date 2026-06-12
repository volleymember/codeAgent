package com.codeagent.memory;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.AgentMemoryNote;
import com.codeagent.memory.model.CoreMemoryItem;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryCenterStats;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryFeedbackRequest;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import com.codeagent.storage.repository.MemoryCoreRepository;
import com.codeagent.storage.repository.MemoryEpisodeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆中心服务。
 *
 * <p>作为 memory 模块的统一入口，负责整合核心记忆、工作记忆、经验记忆和召回审计。
 * Agent 在执行任务前，可以通过该服务构建完整的记忆上下文；任务结束后，也可以通过该服务
 * 将有价值的分析结果沉淀为新的经验记忆。</p>
 *
 * <p>各类记忆的职责如下：</p>
 * <ul>
 *     <li>核心记忆：长期稳定的规则、偏好、约束和项目背景。</li>
 *     <li>工作记忆：当前会话中的临时上下文、阶段性状态和 Agent 备注。</li>
 *     <li>经验记忆：历史任务、问题、故障、根因和修复方案等案例型记忆。</li>
 *     <li>召回审计：记录每次记忆召回的输入、输出和耗时，便于排查与评估。</li>
 * </ul>
 */
@Service
public class MemoryCenterService {
    private final CoreMemoryService coreMemoryService;
    private final WorkingMemoryService workingMemoryService;
    private final EpisodicMemoryService episodicMemoryService;
    private final MemoryRecallAuditService memoryRecallAuditService;
    private final MemoryProperties properties;
    private final MemoryCoreRepository coreRepository;
    private final MemoryEpisodeRepository episodeRepository;

    /**
     * 创建记忆中心服务实例。
     *
     * @param coreMemoryService       核心记忆服务
     * @param workingMemoryService    工作记忆服务
     * @param episodicMemoryService   经验记忆服务
     * @param memoryRecallAuditService 记忆召回审计服务
     * @param properties              记忆模块配置
     * @param coreRepository          核心记忆仓储
     * @param episodeRepository       经验记忆仓储
     */
    public MemoryCenterService(CoreMemoryService coreMemoryService,
                               WorkingMemoryService workingMemoryService,
                               EpisodicMemoryService episodicMemoryService,
                               MemoryRecallAuditService memoryRecallAuditService,
                               MemoryProperties properties,
                               MemoryCoreRepository coreRepository,
                               MemoryEpisodeRepository episodeRepository) {
        this.coreMemoryService = coreMemoryService;
        this.workingMemoryService = workingMemoryService;
        this.episodicMemoryService = episodicMemoryService;
        this.memoryRecallAuditService = memoryRecallAuditService;
        this.properties = properties;
        this.coreRepository = coreRepository;
        this.episodeRepository = episodeRepository;
    }

    /**
     * 构建 Agent 执行任务所需的记忆上下文。
     *
     * <p>该方法会完成以下步骤：</p>
     * <ol>
     *     <li>加载全局和当前项目下的常驻核心记忆。</li>
     *     <li>根据当前请求召回相关的历史经验记忆。</li>
     *     <li>将本次召回信息合并到当前会话的工作记忆中。</li>
     *     <li>从工作记忆中读取 Agent 阶段性备注。</li>
     *     <li>记录一次记忆召回审计日志。</li>
     * </ol>
     *
     * @param request 记忆召回请求，包含项目、任务类型、查询内容、会话 ID 和症状等信息
     * @return 可注入 Agent 上下文的完整记忆中心上下文
     */
    public MemoryCenterContext buildContext(MemoryRecallRequest request) {
        long startedAt = System.currentTimeMillis();

        // 加载长期常驻核心记忆，例如项目规则、用户偏好、稳定约束等。
        List<CoreMemoryItem> coreRules = coreMemoryService.loadResidentRules(request.projectKey());

        // 召回与当前请求最相关的历史经验，并使用配置限制最大召回数量。
        List<MemoryEpisodeMatch> episodes = episodicMemoryService.recall(new MemoryRecallRequest(
                request.projectKey(),
                request.taskType(),
                request.query(),
                request.sessionId(),
                request.symptoms(),
                Math.min(request.limit(), Math.max(1, properties.getMaxEpisodeRecall())),
                request.taskNo(),
                request.agentName(),
                request.phase()
        ));

        // 如果存在 sessionId，则把本次记忆解析结果写入工作记忆；否则使用空工作记忆。
        Map<String, Object> working = request.sessionId() == null || request.sessionId().isBlank()
                ? new LinkedHashMap<>()
                : workingMemoryService.merge(request.sessionId(), Map.of(
                "projectKey", request.projectKey(),
                "taskType", value(request.taskType()),
                "query", value(request.query()),
                "lastMemoryResolveAt", LocalDateTime.now().toString(),
                "residentCoreRuleCount", coreRules.size(),
                "recalledEpisodeIds", episodes.stream().map(MemoryEpisodeMatch::episodeId).toList()
        ));

        // 组装最终上下文，供 Agent 在后续推理、规划或执行阶段使用。
        MemoryCenterContext context = new MemoryCenterContext(request.sessionId(), request.projectKey(), coreRules, working, episodes,
                readAgentNotes(working));

        // 记录召回审计信息，便于后续分析召回质量和耗时。
        memoryRecallAuditService.record(request, context, System.currentTimeMillis() - startedAt);
        return context;
    }

    /**
     * 向当前会话的工作记忆中追加一条 Agent 备注。
     *
     * <p>Agent 备注通常用于记录某个 Agent 在某个阶段产生的阶段性判断、观察、计划或结论。
     * 这些备注会保存在工作记忆中，后续构建上下文时可被重新读取。</p>
     *
     * @param sessionId 当前会话 ID
     * @param agentName Agent 名称
     * @param phase     当前任务阶段
     * @param note      备注内容
     * @param metadata  附加元数据
     * @return 追加后的 Agent 备注
     */
    public AgentMemoryNote appendAgentNote(String sessionId,
                                           String agentName,
                                           String phase,
                                           String note,
                                           Map<String, Object> metadata) {
        return workingMemoryService.appendAgentNote(sessionId, agentName, phase, note, metadata);
    }

    /**
     * 更新当前会话的工作记忆。
     *
     * <p>当会话 ID 为空时直接忽略更新，避免创建无效的工作记忆记录。</p>
     *
     * @param sessionId 当前会话 ID
     * @param patch     待合并到工作记忆中的增量字段
     */
    public void updateWorkingContext(String sessionId, Map<String, Object> patch) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        workingMemoryService.merge(sessionId, patch);
    }

    /**
     * 根据外部反馈更新指定经验记忆。
     *
     * <p>反馈通常用于提升或降低某条历史经验的可靠性、置信度或状态。
     * 例如用户确认某条经验有效后，可以将其从 candidate 提升为 verified。</p>
     *
     * @param episodeId 经验记忆 ID
     * @param request   反馈请求
     * @return 更新后的经验记忆实体
     */
    public MemoryEpisodeEntity feedback(String episodeId, MemoryFeedbackRequest request) {
        return episodicMemoryService.feedback(episodeId, request);
    }

    /**
     * 查询指定项目的记忆中心统计信息。
     *
     * <p>统计信息包括核心记忆数量、经验记忆数量，以及当前记忆召回相关配置。</p>
     *
     * @param projectKey 项目标识
     * @return 记忆中心统计信息
     */
    public MemoryCenterStats stats(String projectKey) {
        return new MemoryCenterStats(projectKey,
                coreRepository.countByProjectKey(projectKey),
                episodeRepository.countByProjectKey(projectKey),
                properties.getMaxCoreRules(),
                properties.getMaxEpisodeRecall(),
                properties.getMinRecallScore());
    }

    /**
     * 将一次 Bug 分析任务的结果沉淀为经验记忆。
     *
     * <p>该方法会从证据和最终报告中提取症状、疑似原因、建议修复方向和标签，
     * 并创建一条状态为 {@code candidate} 的经验记忆。创建完成后，会再次召回该经验，
     * 返回当前最匹配的一条经验结果。</p>
     *
     * <p>当项目 Key、最终报告或证据列表缺失时，返回 {@code null}，表示无法沉淀有效经验。</p>
     *
     * @param projectKey   项目标识
     * @param taskType     任务类型，例如 debug、review、test 等
     * @param taskNo       任务编号，用于追踪经验来源
     * @param evidence     本次任务使用到的证据列表
     * @param critique     对最终报告的评审信息，可包含 confidence 字段
     * @param finalReport  最终分析报告
     * @return 创建并召回出的经验匹配结果；无法沉淀时返回 {@code null}
     */
    public MemoryEpisodeMatch consolidateBugExperience(String projectKey,
                                                       String taskType,
                                                       String taskNo,
                                                       List<EvidenceItem> evidence,
                                                       Map<String, Object> critique,
                                                       String finalReport) {
        if (projectKey == null || projectKey.isBlank() || finalReport == null || finalReport.isBlank()
                || evidence == null || evidence.isEmpty()) {
            return null;
        }

        // 从任务类型和高分证据中派生问题症状，作为后续召回的主要匹配特征。
        List<String> symptoms = deriveSymptoms(taskType, evidence);

        // 从最终报告中提取根因和修复建议；如果提取失败，则退化为截取报告摘要。
        String rootCause = extractSection(finalReport, "疑似原因", "关键证据");
        String fix = extractSection(finalReport, "建议修复方向", "建议补充测试");
        if (rootCause.isBlank()) {
            rootCause = TokenEstimator.truncateByTokens(finalReport, 240);
        }
        if (fix.isBlank()) {
            fix = TokenEstimator.truncateByTokens(finalReport, 240);
        }

        // 从评审信息中提取置信度。
        Double confidence = confidence(critique);

        // 使用证据来源类型生成标签，例如 log、code、test、metric 等。
        List<String> tags = evidence.stream()
                .map(EvidenceItem::sourceType)
                .filter(Objects::nonNull)
                .map(type -> type.toLowerCase(Locale.ROOT))
                .distinct()
                .limit(12)
                .toList();

        // 创建候选经验记忆。candidate 表示该经验尚未被人工或后续反馈充分验证。
        episodicMemoryService.create(projectKey, symptoms, rootCause, fix,
                "task:" + taskNo, "agent-task://" + taskNo, "candidate", tags, confidence);

        // 创建后立即召回一次，返回最相关的经验匹配结果，方便调用方展示或记录。
        return episodicMemoryService.recall(new MemoryRecallRequest(projectKey, taskType,
                        String.join(" ", symptoms), null, symptoms, 1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据任务类型和证据列表派生问题症状。
     *
     * <p>该方法会优先选取得分最高的证据，将证据标题、摘要和匹配原因合并为症状文本。
     * 最终结果会去重并限制数量，避免经验记忆过长。</p>
     *
     * @param taskType 任务类型
     * @param evidence 证据列表
     * @return 派生出的症状列表
     */
    private List<String> deriveSymptoms(String taskType, List<EvidenceItem> evidence) {
        List<String> symptoms = new ArrayList<>();
        if (taskType != null && !taskType.isBlank()) {
            symptoms.add(taskType);
        }
        evidence.stream()
                .sorted(Comparator.comparingDouble(EvidenceItem::score).reversed())
                .limit(6)
                .map(item -> "%s %s %s".formatted(item.title(), item.summary(), item.matchReason()))
                .map(text -> TokenEstimator.truncateByTokens(text.replaceAll("\\s+", " ").trim(), 80))
                .filter(text -> !text.isBlank())
                .forEach(symptoms::add);
        return symptoms.stream().distinct().limit(8).toList();
    }

    /**
     * 从报告中提取指定标题之间的章节内容。
     *
     * <p>例如从 {@code 疑似原因} 到 {@code 关键证据} 之间提取根因描述，
     * 或从 {@code 建议修复方向} 到 {@code 建议补充测试} 之间提取修复建议。
     * 提取后的内容会去除开头的冒号和空白，并按 token 数截断。</p>
     *
     * @param report     完整报告文本
     * @param startTitle 起始章节标题
     * @param endTitle   结束章节标题；为空时提取到报告结尾
     * @return 提取出的章节内容；未找到起始标题时返回空字符串
     */
    private String extractSection(String report, String startTitle, String endTitle) {
        int start = report.indexOf(startTitle);
        if (start < 0) {
            return "";
        }
        int contentStart = Math.min(report.length(), start + startTitle.length());
        int end = endTitle == null ? -1 : report.indexOf(endTitle, contentStart);
        String section = end > contentStart ? report.substring(contentStart, end) : report.substring(contentStart);
        return TokenEstimator.truncateByTokens(section.replaceAll("^[：:\\s]+", "").trim(), 260);
    }

    /**
     * 从评审信息中读取置信度。
     *
     * <p>当前只读取 {@code confidence} 字段。字段可以是数字，也可以是可解析为数字的字符串。
     * 当字段不存在或无法解析时，返回 {@code null}。</p>
     *
     * @param critique 评审信息
     * @return 置信度分数；不存在或解析失败时返回 {@code null}
     */
    private Double confidence(Map<String, Object> critique) {
        Object value = critique == null ? null : critique.get("confidence");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 从工作记忆中读取 Agent 备注列表。
     *
     * <p>工作记忆中的 {@code agentNotes} 字段应为列表结构，每一项包含 agentName、phase、note、
     * metadata 和 createdAt 等字段。无法识别的条目会被跳过。</p>
     *
     * @param working 工作记忆内容
     * @return Agent 备注列表
     */
    private List<AgentMemoryNote> readAgentNotes(Map<String, Object> working) {
        Object raw = working.get("agentNotes");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<AgentMemoryNote> notes = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            notes.add(new AgentMemoryNote(
                    value(map.get("agentName")),
                    value(map.get("phase")),
                    value(map.get("note")),
                    readMetadata(map.get("metadata")),
                    readTime(map.get("createdAt"))
            ));
        }
        return notes;
    }

    /**
     * 读取备注元数据。
     *
     * <p>当输入已经是 Map 时，会转换为 {@code Map<String, Object>}。
     * 当输入是字符串时，会尝试按 JSON 对象解析。
     * 如果 JSON 解析失败，则将原始值放入 {@code raw} 字段返回，避免信息丢失。</p>
     *
     * @param value 原始元数据
     * @return 标准化后的元数据 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            map.forEach((key, item) -> metadata.put(String.valueOf(key), item));
            return metadata;
        }
        if (value == null) {
            return Map.of();
        }
        try {
            return JsonSupport.mapper().readValue(String.valueOf(value),
                    JsonSupport.mapper().getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) {
            return Map.of("raw", String.valueOf(value));
        }
    }

    /**
     * 读取备注创建时间。
     *
     * <p>当输入为空或无法解析为 {@link LocalDateTime} 时，返回当前时间作为兜底值。</p>
     *
     * @param value 原始时间值
     * @return 解析后的时间；解析失败时返回当前时间
     */
    private LocalDateTime readTime(Object value) {
        if (value == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    /**
     * 将对象安全转换为字符串。
     *
     * @param value 原始对象
     * @return 字符串值；当对象为空时返回空字符串
     */
    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}