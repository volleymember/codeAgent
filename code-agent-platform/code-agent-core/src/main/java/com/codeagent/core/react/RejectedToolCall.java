package com.codeagent.core.react;

import java.util.Map;

public record RejectedToolCall(
        String toolName,
        Map<String, Object> input,
        String rejectedReason
) {
    public RejectedToolCall {
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}
