package com.codeagent.memory.model;

public record CompressedAgentNote(
        String agentName,
        String phase,
        String note,
        String createdAt
) {
}
