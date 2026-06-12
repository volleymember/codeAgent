package com.codeagent.core.parallel;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.enums.ToolCallStatus;
import com.codeagent.mcp.model.ToolCallResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentExecutionResult(
        AgentWorkItem workItem,
        ToolCallResult toolResult,
        int attempts,
        boolean success,
        boolean requiredFailure,
        long latencyMs,
        String errorMessage
) {
    public AgentExecutionResult {
        attempts = Math.max(1, attempts);
        success = toolResult != null && ToolCallStatus.SUCCESS.name().equals(toolResult.status());
        requiredFailure = workItem != null && workItem.required() && !success;
        latencyMs = Math.max(0, latencyMs);
        errorMessage = errorMessage == null ? (toolResult == null ? null : toolResult.errorMessage()) : errorMessage;
    }

    public List<EvidenceItem> evidence() {
        return toolResult == null ? List.of() : toolResult.evidence();
    }

    public Map<String, Object> auditSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("stepId", workItem.stepId());
        summary.put("agentName", workItem.agentName());
        summary.put("toolName", workItem.toolName());
        summary.put("source", workItem.source().name());
        summary.put("workType", workItem.workType().name());
        summary.put("status", toolResult == null ? ToolCallStatus.FAILED.name() : toolResult.status());
        summary.put("attempts", attempts);
        summary.put("required", workItem.required());
        summary.put("evidenceCount", evidence().size());
        summary.put("latencyMs", latencyMs);
        summary.put("errorMessage", errorMessage == null ? "" : errorMessage);
        return summary;
    }
}
