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

@Component
public class ToolRegistry {
    private final Map<String, ToolExecutor> executors;

    public ToolRegistry(List<ToolExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toUnmodifiableMap(executor -> executor.definition().name(), Function.identity()));
    }

    public ToolExecutor get(String name) {
        ToolExecutor executor = executors.get(name);
        if (executor == null) {
            throw new BusinessException("TOOL_NOT_REGISTERED", "Tool is not registered: " + name);
        }
        return executor;
    }

    public List<ToolDefinition> list() {
        return executors.values().stream()
                .map(ToolExecutor::definition)
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }
}
