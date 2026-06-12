package com.codeagent.core.parallel;

import com.codeagent.rag.search.RagSearchRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public record AgentWorkItem(
        String taskNo,
        String sessionId,
        String stepId,
        String agentName,
        AgentWorkSource source,
        AgentWorkType workType,
        String toolName,
        Map<String, Object> input,
        RagSearchRequest ragSearchRequest,
        boolean required,
        int maxAttempts,
        long timeoutMs,
        double routeScore,
        String routeReason,
        int estimatedOutputTokens
) {
    public AgentWorkItem {
        taskNo = present(taskNo, "UNKNOWN");
        sessionId = present(sessionId, "UNKNOWN");
        stepId = present(stepId, "UNKNOWN");
        agentName = present(agentName, "ToolAgent");
        source = source == null ? AgentWorkSource.MCP_TOOL : source;
        workType = workType == null ? AgentWorkType.GENERIC_TOOL : workType;
        toolName = present(toolName, source.name().toLowerCase());
        input = input == null ? Map.of() : Map.copyOf(input);
        maxAttempts = Math.max(1, maxAttempts);
        timeoutMs = Math.max(1000, timeoutMs);
        routeScore = Math.max(0.0, Math.min(1.0, routeScore));
        routeReason = present(routeReason, "parallel evidence task");
        estimatedOutputTokens = Math.max(0, estimatedOutputTokens);
    }

    public Map<String, Object> auditInput() {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("source", source.name());
        audit.put("workType", workType.name());
        audit.put("toolName", toolName);
        audit.put("required", required);
        audit.put("maxAttempts", maxAttempts);
        audit.put("timeoutMs", timeoutMs);
        audit.put("routeScore", routeScore);
        audit.put("routeReason", routeReason);
        audit.put("estimatedOutputTokens", estimatedOutputTokens);
        if (source == AgentWorkSource.RAG_SEARCH && ragSearchRequest != null) {
            audit.put("ragSearchRequest", ragSearchRequest);
        } else {
            audit.put("input", input);
        }
        return audit;
    }

    private static String present(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }
}
