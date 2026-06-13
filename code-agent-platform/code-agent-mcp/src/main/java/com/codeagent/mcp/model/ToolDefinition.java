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
        boolean highCost,
        List<String> optionalInputs,
        List<String> outputFacts,
        String toolType,
        List<String> allowedIntentTypes,
        int cost
) {
    public ToolDefinition(String name,
                          String platform,
                          String description,
                          List<String> requiredInputs,
                          long timeoutMs) {
        this(name, platform, description, requiredInputs, timeoutMs, List.of(), 800, false);
    }

    public ToolDefinition(String name,
                          String platform,
                          String description,
                          List<String> requiredInputs,
                          long timeoutMs,
                          List<String> tags,
                          int estimatedOutputTokens,
                          boolean highCost) {
        this(name, platform, description, requiredInputs, timeoutMs, tags, estimatedOutputTokens, highCost,
                List.of(), List.of(), platform, List.of(), highCost ? 8 : 3);
    }

    public ToolDefinition {
        requiredInputs = requiredInputs == null ? List.of() : List.copyOf(requiredInputs);
        optionalInputs = optionalInputs == null ? List.of() : List.copyOf(optionalInputs);
        outputFacts = outputFacts == null ? List.of() : List.copyOf(outputFacts);
        allowedIntentTypes = allowedIntentTypes == null ? List.of() : List.copyOf(allowedIntentTypes);
        tags = tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();
        estimatedOutputTokens = Math.max(1, estimatedOutputTokens);
        toolType = toolType == null || toolType.isBlank() ? platform : toolType;
        cost = Math.max(1, cost);
    }
}
