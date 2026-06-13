package com.codeagent.memory.model;

import java.util.List;

/**
 * 记忆召回请求。
 *
 * <p>该 record 用于向 Memory Center 发起上下文召回请求。
 * Memory Center 可以根据项目、任务类型、查询文本、症状描述、会话和执行阶段等信息，
 * 召回相关的核心规则、历史经验、工作记忆或相似问题。</p>
 *
 * @param projectKey 项目标识，用于限定记忆召回范围
 * @param taskType   任务类型，例如缺陷分析、构建诊断、代码审查等
 * @param query      召回查询文本，通常由任务输入、链接、构建信息等拼接而成
 * @param sessionId  当前 Agent 会话 ID，用于关联会话内工作记忆
 * @param symptoms   当前任务中的症状、现象或证据摘要，用于召回相似历史经验
 * @param limit      最大召回数量；小于等于 0 时默认使用 6
 * @param taskNo     当前任务编号，可用于审计和记忆轨迹记录
 * @param agentName  发起召回的 Agent 名称；为空时默认为 UNKNOWN
 * @param phase      当前执行阶段；为空时默认为 UNKNOWN
 */
public record MemoryRecallRequest(
        String projectKey,
        String taskType,
        String query,
        String sessionId,
        List<String> symptoms,
        int limit,
        String taskNo,
        String agentName,
        String phase
) {

    /**
     * 兼容旧调用方式的便捷构造器。
     *
     * <p>当调用方暂时不需要传入 taskNo、agentName 和 phase 时，
     * 可以使用该构造器创建请求对象。这些字段会在主构造器中统一补充默认值。</p>
     *
     * @param projectKey 项目标识
     * @param taskType   任务类型
     * @param query      召回查询文本
     * @param sessionId  当前会话 ID
     * @param symptoms   症状或证据摘要列表
     * @param limit      最大召回数量
     */
    public MemoryRecallRequest(String projectKey,
                               String taskType,
                               String query,
                               String sessionId,
                               List<String> symptoms,
                               int limit) {
        this(projectKey, taskType, query, sessionId, symptoms, limit, null, null, null);
    }

    /**
     * 紧凑构造器。
     *
     * <p>用于在对象创建时对部分字段进行规范化：</p>
     * <ul>
     *     <li>symptoms 为 null 时转换为空列表</li>
     *     <li>symptoms 非空时复制为不可变列表，避免外部修改</li>
     *     <li>limit 小于等于 0 时默认设置为 6</li>
     *     <li>agentName 为空时默认设置为 UNKNOWN</li>
     *     <li>phase 为空时默认设置为 UNKNOWN</li>
     * </ul>
     */
    public MemoryRecallRequest {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        limit = limit <= 0 ? 6 : limit;
        agentName = agentName == null || agentName.isBlank() ? "UNKNOWN" : agentName;
        phase = phase == null || phase.isBlank() ? "UNKNOWN" : phase;
    }
}