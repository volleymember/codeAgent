package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryRecallRecordEntity;
import com.codeagent.storage.repository.MemoryRecallRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆召回审计服务。
 *
 * <p>该服务用于记录 Memory Center 每次召回的审计信息，
 * 包括任务编号、会话编号、项目、任务类型、Agent 名称、执行阶段、查询文本、
 * 召回的核心规则数量、情节记忆数量、最高匹配分数和召回耗时等。</p>
 *
 * <p>这些审计数据可用于后续排查 Agent 决策过程、分析记忆召回效果、
 * 统计不同项目或任务中的记忆使用情况。</p>
 */
@Service
public class MemoryRecallAuditService {

    /**
     * 记忆召回记录仓储。
     */
    private final MemoryRecallRecordRepository repository;

    /**
     * 创建记忆召回审计服务。
     *
     * @param repository 记忆召回记录仓储
     */
    public MemoryRecallAuditService(MemoryRecallRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一次 Memory Center 召回审计信息。
     *
     * <p>该方法会从召回请求和召回上下文中提取关键字段，
     * 并保存为 MemoryRecallRecordEntity。</p>
     *
     * <p>记录内容包括：</p>
     * <ul>
     *     <li>任务编号和会话编号</li>
     *     <li>项目和任务类型</li>
     *     <li>发起召回的 Agent 和执行阶段</li>
     *     <li>召回查询文本</li>
     *     <li>核心规则数量</li>
     *     <li>情节记忆数量</li>
     *     <li>被召回的情节记忆 ID 列表</li>
     *     <li>最高召回分数</li>
     *     <li>召回耗时</li>
     * </ul>
     *
     * @param request   记忆召回请求
     * @param context   Memory Center 返回的上下文
     * @param latencyMs 本次召回耗时，单位毫秒
     * @return 保存后的记忆召回记录实体
     */
    public MemoryRecallRecordEntity record(MemoryRecallRequest request, MemoryCenterContext context, long latencyMs) {
        MemoryRecallRecordEntity entity = new MemoryRecallRecordEntity();

        entity.taskNo = request.taskNo();
        entity.sessionId = request.sessionId();
        entity.projectKey = request.projectKey();
        entity.taskType = request.taskType();
        entity.agentName = request.agentName();
        entity.phase = request.phase();
        entity.queryText = request.query();

        // 记录本次召回命中的核心规则数量
        entity.coreRuleCount = context.coreRules().size();

        // 记录本次召回命中的历史情节记忆数量
        entity.episodeCount = context.recalledEpisodes().size();

        // 保存召回到的情节记忆 ID，便于后续追溯 Agent 使用了哪些历史经验
        entity.recalledEpisodeIds = JsonSupport.toJson(context.recalledEpisodes().stream()
                .map(MemoryEpisodeMatch::episodeId)
                .toList());

        // 记录最高匹配分数；如果没有召回结果，则默认为 0
        entity.maxScore = context.recalledEpisodes().stream()
                .mapToDouble(MemoryEpisodeMatch::score)
                .max()
                .orElse(0.0);

        entity.latencyMs = latencyMs;
        entity.createdAt = LocalDateTime.now();

        return repository.save(entity);
    }

    /**
     * 查询指定项目下最近的记忆召回记录。
     *
     * @param projectKey 项目标识
     * @return 最近 100 条召回记录，按 ID 倒序排列
     */
    public List<MemoryRecallRecordEntity> byProject(String projectKey) {
        return repository.findTop100ByProjectKeyOrderByIdDesc(projectKey);
    }

    /**
     * 查询指定任务下的记忆召回记录。
     *
     * @param taskNo 任务编号
     * @return 当前任务的召回记录，按 ID 倒序排列
     */
    public List<MemoryRecallRecordEntity> byTask(String taskNo) {
        return repository.findByTaskNoOrderByIdDesc(taskNo);
    }

    /**
     * 查询指定会话下的记忆召回记录。
     *
     * @param sessionId 会话编号
     * @return 当前会话的召回记录，按 ID 倒序排列
     */
    public List<MemoryRecallRecordEntity> bySession(String sessionId) {
        return repository.findBySessionIdOrderByIdDesc(sessionId);
    }
}