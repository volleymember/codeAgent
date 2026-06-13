package com.codeagent.mcp.router;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.tool.ToolExecutor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具注册表。
 *
 * <p>该组件负责集中管理系统中所有实现了 {@link ToolExecutor} 的工具执行器。
 * Spring 会将所有 ToolExecutor Bean 注入到构造方法中，本类再按工具名称构建只读 Map，
 * 供后续根据工具名查找执行器或列出全部工具定义。</p>
 *
 * <p>它通常被 MCP 路由层或 Agent 工具调用流程使用，用于把计划中的 toolName
 * 映射到具体的 ToolExecutor 实例。</p>
 */
@Component
public class ToolRegistry {

    /**
     * 工具执行器映射表。
     *
     * <p>key 为工具定义中的 name，value 为对应的 ToolExecutor。
     * 该 Map 在构造完成后不可修改，避免运行期间工具注册关系被意外改变。</p>
     */
    private final Map<String, ToolExecutor> executors;

    /**
     * 创建工具注册表。
     *
     * <p>构造时会收集 Spring 容器中所有 ToolExecutor，并按照工具名称建立索引。
     * 如果存在重复工具名，Collectors.toUnmodifiableMap 会抛出异常，从而在启动阶段暴露配置问题。</p>
     *
     * @param executors Spring 注入的工具执行器列表
     */
    public ToolRegistry(List<ToolExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toUnmodifiableMap(executor -> executor.definition().name(), Function.identity()));
    }

    /**
     * 根据工具名称获取对应的工具执行器。
     *
     * @param name 工具名称
     * @return 对应的 ToolExecutor
     * @throws BusinessException 当指定工具未注册时抛出
     */
    public ToolExecutor get(String name) {
        ToolExecutor executor = executors.get(name);

        if (executor == null) {
            throw new BusinessException("TOOL_NOT_REGISTERED", "Tool is not registered: " + name);
        }

        return executor;
    }

    /**
     * 列出所有已注册工具的定义信息。
     *
     * <p>返回结果会按照工具名称升序排序，便于前端展示、Agent 规划或接口调试。</p>
     *
     * @return 已注册工具定义列表
     */
    public List<ToolDefinition> list() {
        return executors.values().stream()
                .map(ToolExecutor::definition)
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }
}