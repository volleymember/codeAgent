package com.codeagent.mcp.model;

import java.util.Map;

public record ToolRouteRequest(
        String taskType,
        String projectKey,
        String userGoal,
        Map<String, Object> availableInputs,
        int maxTools
) {
    public ToolRouteRequest {
        availableInputs = availableInputs == null ? Map.of() : Map.copyOf(availableInputs);
        maxTools = maxTools <= 0 ? 12 : maxTools;
    }
}
