package com.codeagent.core.react;

import java.util.List;
import java.util.Map;

public record ToolPlanCandidate(
        String toolName,
        String purpose,
        Map<String, Object> input,
        List<String> expectedOutput,
        int priority,
        String whyNeeded
) {
    public ToolPlanCandidate {
        input = input == null ? Map.of() : Map.copyOf(input);
        expectedOutput = expectedOutput == null ? List.of() : List.copyOf(expectedOutput);
        priority = Math.max(1, priority);
    }
}
