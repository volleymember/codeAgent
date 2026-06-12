package com.codeagent.core.parallel;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.model.ToolCallResult;

import java.util.List;
import java.util.Map;

public record ParallelAgentExecutionReport(
        String taskNo,
        int submittedCount,
        int successfulCount,
        int failedCount,
        int requiredFailureCount,
        long latencyMs,
        List<AgentExecutionResult> branchResults,
        List<ToolCallResult> toolResults,
        List<EvidenceItem> evidence,
        List<AgentFinding> findings,
        Map<String, Object> stats
) {
    public ParallelAgentExecutionReport {
        branchResults = branchResults == null ? List.of() : List.copyOf(branchResults);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        findings = findings == null ? List.of() : List.copyOf(findings);
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }
}
