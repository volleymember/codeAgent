package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.parallel.AgentFinding;
import com.codeagent.mcp.model.ToolCallResult;

import java.util.List;
import java.util.Map;

public record MCPReActResult(
        List<ToolPlan> executedPlans,
        List<ToolCallResult> toolResults,
        List<EvidenceItem> evidence,
        List<AgentFinding> findings,
        List<RejectedToolCall> rejectedToolCalls,
        Map<String, Object> knownFacts,
        List<String> missingFacts,
        ObservationReflection reflection,
        int rounds,
        boolean stoppedBySufficiency
) {
    public MCPReActResult {
        executedPlans = executedPlans == null ? List.of() : List.copyOf(executedPlans);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        findings = findings == null ? List.of() : List.copyOf(findings);
        rejectedToolCalls = rejectedToolCalls == null ? List.of() : List.copyOf(rejectedToolCalls);
        knownFacts = knownFacts == null ? Map.of() : Map.copyOf(knownFacts);
        missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
    }
}
