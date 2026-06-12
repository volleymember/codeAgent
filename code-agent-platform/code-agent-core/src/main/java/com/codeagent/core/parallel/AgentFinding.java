package com.codeagent.core.parallel;

import java.util.List;
import java.util.Map;

public record AgentFinding(
        String agentName,
        AgentWorkType workType,
        String status,
        String claim,
        double confidence,
        List<String> evidenceRefs,
        List<String> missingEvidence,
        Map<String, Object> metadata
) {
    public AgentFinding {
        agentName = present(agentName, "ToolAgent");
        workType = workType == null ? AgentWorkType.GENERIC_TOOL : workType;
        status = present(status, "UNKNOWN");
        claim = present(claim, "No claim produced.");
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static String present(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
