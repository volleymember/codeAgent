package com.codeagent.core.agent;

import java.util.Map;

public record ToolPlan(
        String stepId,
        String agentName,
        String toolName,
        Map<String, Object> input,
        boolean required,
        double routeScore,
        String routeReason,
        int estimatedOutputTokens
) {
    public ToolPlan(String stepId,
                    String agentName,
                    String toolName,
                    Map<String, Object> input,
                    boolean required) {
        this(stepId, agentName, toolName, input, required, 0.0, "legacy planner", 0);
    }

    public ToolPlan {
        input = input == null ? Map.of() : Map.copyOf(input);
        routeScore = Math.max(0.0, Math.min(1.0, routeScore));
        routeReason = routeReason == null || routeReason.isBlank() ? "MCP Router selected tool." : routeReason;
        estimatedOutputTokens = Math.max(0, estimatedOutputTokens);
    }
}
