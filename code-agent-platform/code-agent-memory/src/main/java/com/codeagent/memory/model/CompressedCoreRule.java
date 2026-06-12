package com.codeagent.memory.model;

import java.util.List;

public record CompressedCoreRule(
        String type,
        String content,
        List<String> tags,
        int priority,
        String sourceUri
) {
    public CompressedCoreRule {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
