package com.codeagent.memory.model;

import java.time.LocalDateTime;
import java.util.Map;

public record AgentMemoryNote(
        String agentName,
        String phase,
        String note,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
    public AgentMemoryNote {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
