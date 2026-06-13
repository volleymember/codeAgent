package com.codeagent.memory.model;

import java.util.List;

/**
 * 核心记忆项。
 *
 * <p>该 record 用于表示 Memory Center 中长期保留的核心规则、经验或约束。
 * 这些内容通常会在 Agent 执行任务时被召回，用于指导规划、证据判断和最终报告生成。</p>
 *
 * @param id         核心记忆 ID
 * @param projectKey 所属项目标识，可用于区分不同项目的记忆
 * @param type       记忆类型，例如规则、约束、经验、偏好等
 * @param content    记忆正文内容
 * @param tags       标签列表，用于分类、检索和过滤
 * @param priority   优先级，数值越高通常表示越重要
 * @param sourceUri  来源 URI，用于追溯该记忆的来源
 */
public record CoreMemoryItem(
        Long id,
        String projectKey,
        String type,
        String content,
        List<String> tags,
        int priority,
        String sourceUri
) {

    /**
     * 紧凑构造器。
     *
     * <p>用于在 record 创建时规范化 tags 字段：</p>
     * <ul>
     *     <li>当 tags 为 null 时，转换为空列表</li>
     *     <li>当 tags 非空时，复制为不可变列表，避免外部修改影响当前对象</li>
     * </ul>
     */
    public CoreMemoryItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
