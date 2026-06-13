package com.codeagent.core.react;

import com.codeagent.mcp.model.ToolDefinition;

import java.util.Map;

public record ToolCallValidation(
        boolean accepted,
        ToolPlanCandidate candidate,
        ToolDefinition definition,
        Map<String, Object> resolvedInput,
        String rejectedReason,
        double inputCompletenessScore,
        double intentMatchScore,
        boolean duplicate
) {
    public ToolCallValidation {
        resolvedInput = resolvedInput == null ? Map.of() : Map.copyOf(resolvedInput);
    }
}
