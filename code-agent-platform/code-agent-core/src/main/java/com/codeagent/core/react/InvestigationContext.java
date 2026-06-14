package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.understanding.IntentClassificationResult;
import com.codeagent.core.understanding.ProjectContext;
import com.codeagent.core.understanding.QueryUnderstandingResult;
import com.codeagent.core.understanding.ResolvedTimeRange;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolCallResult;

import java.util.List;
import java.util.Map;

public record InvestigationContext(
        String taskNo,
        String sessionId,
        CreateAgentTaskCommand command,
        QueryUnderstandingResult queryUnderstanding,
        IntentClassificationResult intentClassification,
        IntentLeafView intentLeaf,
        ResolvedTimeRange timeRange,
        ProjectContext projectContext,
        MemoryCenterContext memoryContext,
        Map<String, Object> knownFacts,
        List<String> missingFacts,
        List<ToolCallResult> previousToolCalls,
        List<RejectedToolCall> rejectedToolCalls,
        List<String> successfulToolCallKeys,
        List<EvidenceItem> evidence,
        List<String> recentTurns,
        String compressedSummary
) {
    public InvestigationContext(String taskNo,
                                String sessionId,
                                CreateAgentTaskCommand command,
                                QueryUnderstandingResult queryUnderstanding,
                                IntentClassificationResult intentClassification,
                                IntentLeafView intentLeaf,
                                ResolvedTimeRange timeRange,
                                ProjectContext projectContext,
                                MemoryCenterContext memoryContext,
                                Map<String, Object> knownFacts,
                                List<String> missingFacts,
                                List<ToolCallResult> previousToolCalls,
                                List<RejectedToolCall> rejectedToolCalls,
                                List<EvidenceItem> evidence,
                                List<String> recentTurns,
                                String compressedSummary) {
        this(taskNo, sessionId, command, queryUnderstanding, intentClassification, intentLeaf, timeRange,
                projectContext, memoryContext, knownFacts, missingFacts, previousToolCalls, rejectedToolCalls,
                List.of(), evidence, recentTurns, compressedSummary);
    }

    public InvestigationContext {
        knownFacts = knownFacts == null ? Map.of() : Map.copyOf(knownFacts);
        missingFacts = missingFacts == null ? List.of() : List.copyOf(missingFacts);
        previousToolCalls = previousToolCalls == null ? List.of() : List.copyOf(previousToolCalls);
        rejectedToolCalls = rejectedToolCalls == null ? List.of() : List.copyOf(rejectedToolCalls);
        successfulToolCallKeys = successfulToolCallKeys == null ? List.of() : List.copyOf(successfulToolCallKeys);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
        compressedSummary = compressedSummary == null ? "" : compressedSummary;
    }
}
