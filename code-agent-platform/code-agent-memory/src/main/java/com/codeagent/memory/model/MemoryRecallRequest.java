package com.codeagent.memory.model;

import java.util.List;

public record MemoryRecallRequest(
        String projectKey,
        String taskType,
        String query,
        String sessionId,
        List<String> symptoms,
        int limit,
        String taskNo,
        String agentName,
        String phase
) {
    public MemoryRecallRequest(String projectKey,
                               String taskType,
                               String query,
                               String sessionId,
                               List<String> symptoms,
                               int limit) {
        this(projectKey, taskType, query, sessionId, symptoms, limit, null, null, null);
    }

    public MemoryRecallRequest {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        limit = limit <= 0 ? 6 : limit;
        agentName = agentName == null || agentName.isBlank() ? "UNKNOWN" : agentName;
        phase = phase == null || phase.isBlank() ? "UNKNOWN" : phase;
    }
}
