package com.codeagent.memory.model;

public record MemoryFeedbackRequest(
        boolean helpful,
        String agentName,
        String reason
) {
}
