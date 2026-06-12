package com.codeagent.mcp.model;

import java.util.List;

public record ToolDefinition(
        String name,
        String platform,
        String description,
        List<String> requiredInputs,
        long timeoutMs,
        List<String> tags,
        int estimatedOutputTokens,
        boolean highCost
) {
    public ToolDefinition(String name,
                          String platform,
                          String description,
                          List<String> requiredInputs,
                          long timeoutMs) {
        this(name, platform, description, requiredInputs, timeoutMs, List.of(), 800, false);
    }

    public ToolDefinition {
        requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
        tags = tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();
        estimatedOutputTokens = Math.max(1, estimatedOutputTokens);
    }
}
