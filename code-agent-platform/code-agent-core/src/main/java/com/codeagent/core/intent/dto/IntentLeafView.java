package com.codeagent.core.intent.dto;

import java.util.List;

/**
 * 意图叶子节点视图。
 *
 * <p>该 record 用于对外展示或传递当前启用意图树中的叶子节点信息。
 * 叶子节点通常代表一个可被意图分类器选中的具体业务意图。</p>
 *
 * <p>相比数据库中的 IntentNodeEntity，该视图已经将 JSON 字符串字段转换为 List，
 * 并补充了节点路径 nodePath，方便前端展示、LLM 分类提示词构造和后续工具规划使用。</p>
 *
 * @param treeCode                 所属意图树编码
 * @param version                  所属意图树版本
 * @param nodeCode                 节点编码，通常作为意图唯一标识
 * @param nodePath                 节点路径，例如 ROOT/BUILD/BUILD_FAILURE
 * @param nodeName                 节点名称，用于展示或提示词描述
 * @param description              节点描述，用于说明该意图适用的业务场景
 * @param keywords                 关键词列表，用于意图匹配或提示词增强
 * @param exampleQueries           示例查询列表，用于帮助模型理解该意图适用的问题表达
 * @param defaultTimeRangeHours    默认时间范围，单位小时，可用于日志、监控等工具查询
 * @param allowedToolTypes         该意图允许使用的工具类型列表
 * @param requiredEvidenceTypes    该意图通常需要收集的证据类型列表
 * @param preferredDiscoveryTools  该意图优先使用的运行时对象发现工具
 * @param preferredAnalysisTools   该意图优先使用的分析工具
 * @param requiredConfigFields     该意图必须具备的项目工具配置字段
 */
public record IntentLeafView(
        String treeCode,
        Integer version,
        String nodeCode,
        String nodePath,
        String nodeName,
        String description,
        List<String> keywords,
        List<String> exampleQueries,
        Integer defaultTimeRangeHours,
        List<String> allowedToolTypes,
        List<String> requiredEvidenceTypes,
        List<String> preferredDiscoveryTools,
        List<String> preferredAnalysisTools,
        List<String> requiredConfigFields
) {
    public IntentLeafView(String treeCode,
                          Integer version,
                          String nodeCode,
                          String nodePath,
                          String nodeName,
                          String description,
                          List<String> keywords,
                          List<String> exampleQueries,
                          Integer defaultTimeRangeHours,
                          List<String> allowedToolTypes,
                          List<String> requiredEvidenceTypes) {
        this(treeCode, version, nodeCode, nodePath, nodeName, description, keywords, exampleQueries,
                defaultTimeRangeHours, allowedToolTypes, requiredEvidenceTypes, List.of(), List.of(), List.of());
    }

    /**
     * 紧凑构造器。
     *
     * <p>用于在对象创建时规范化列表字段：</p>
     * <ul>
     *     <li>当列表字段为 null 时，转换为空列表</li>
     *     <li>当列表字段非 null 时，复制为不可变列表，避免外部修改影响当前对象</li>
     * </ul>
     */
    public IntentLeafView {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        exampleQueries = exampleQueries == null ? List.of() : List.copyOf(exampleQueries);
        allowedToolTypes = allowedToolTypes == null ? List.of() : List.copyOf(allowedToolTypes);
        requiredEvidenceTypes = requiredEvidenceTypes == null ? List.of() : List.copyOf(requiredEvidenceTypes);
        preferredDiscoveryTools = preferredDiscoveryTools == null ? List.of() : List.copyOf(preferredDiscoveryTools);
        preferredAnalysisTools = preferredAnalysisTools == null ? List.of() : List.copyOf(preferredAnalysisTools);
        requiredConfigFields = requiredConfigFields == null ? List.of() : List.copyOf(requiredConfigFields);
    }
}
