package com.codeagent.mcp.model;

import java.util.Map;

public record ToolCallRequest(
        String taskNo,
        String toolName,
        Map<String, Object> input
) {
    public String stringInput(String key) {
        Object value = input == null ? null : input.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
