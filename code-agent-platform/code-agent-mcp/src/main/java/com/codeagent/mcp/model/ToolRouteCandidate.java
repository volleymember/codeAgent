package com.codeagent.mcp.model;

import java.util.Map;

public record ToolRouteCandidate(
        String toolName,
        String platform,
        Map<String, Object> input,
        boolean required,
        double score,
        String reason,
        int estimatedOutputTokens,
        boolean highCost
) {
    public ToolRouteCandidate {
        input = input == null ? Map.of() : Map.copyOf(input);
        score = Math.max(0.0, Math.min(1.0, score));
        reason = reason == null || reason.isBlank() ? "MCP route matched available inputs." : reason;
        estimatedOutputTokens = Math.max(1, estimatedOutputTokens);
    }
}
