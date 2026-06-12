package com.codeagent.memory.model;

import java.util.List;

public record CoreMemoryItem(
        Long id,
        String projectKey,
        String type,
        String content,
        List<String> tags,
        int priority,
        String sourceUri
) {
    public CoreMemoryItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
