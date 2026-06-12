package com.codeagent.core.agent;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.parallel.AgentFinding;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolCallResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CritiqueAgent {
    public Map<String, Object> critique(List<ToolPlan> plans, List<ToolCallResult> results, List<EvidenceItem> evidence) {
        return critique(plans, results, evidence, null, List.of());
    }

    public Map<String, Object> critique(List<ToolPlan> plans,
                                        List<ToolCallResult> results,
                                        List<EvidenceItem> evidence,
                                        MemoryCenterContext memoryContext) {
        return critique(plans, results, evidence, memoryContext, List.of());
    }

    public Map<String, Object> critique(List<ToolPlan> plans,
                                        List<ToolCallResult> results,
                                        List<EvidenceItem> evidence,
                                        MemoryCenterContext memoryContext,
                                        List<AgentFinding> findings) {
        long requiredPlans = plans.stream().filter(ToolPlan::required).count();
        long requiredSuccess = plans.stream()
                .filter(ToolPlan::required)
                .filter(plan -> results.stream().anyMatch(result -> plan.toolName().equals(result.toolName()) && "SUCCESS".equals(result.status())))
                .count();
        double confidence = requiredPlans == 0 ? 0.0 : Math.min(0.95, 0.45 + 0.5 * requiredSuccess / requiredPlans);
        List<String> missingEvidence = missingEvidence(requiredPlans, requiredSuccess, evidence, findings);
        boolean enough = requiredPlans > 0 && requiredSuccess == requiredPlans && !evidence.isEmpty() && missingEvidence.isEmpty();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("confidence", confidence);
        payload.put("requiredToolSuccess", requiredSuccess);
        payload.put("requiredToolTotal", requiredPlans);
        payload.put("evidenceCount", evidence.size());
        payload.put("agentFindingCount", findings == null ? 0 : findings.size());
        payload.put("agentFindings", findings == null ? List.of() : findings);
        payload.put("memory", Map.of(
                "coreRuleCount", memoryContext == null ? 0 : memoryContext.coreRules().size(),
                "recalledBugEpisodeCount", memoryContext == null ? 0 : memoryContext.recalledEpisodes().size(),
                "sharedAgentNoteCount", memoryContext == null ? 0 : memoryContext.agentNotes().size()
        ));
        payload.put("decision", enough ? "FINALIZE" : "REPLAN_REQUIRED");
        payload.put("missingEvidence", missingEvidence);
        return payload;
    }

    private List<String> missingEvidence(long requiredPlans,
                                         long requiredSuccess,
                                         List<EvidenceItem> evidence,
                                         List<AgentFinding> findings) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (requiredPlans == 0) {
            missing.add("No required evidence tools were selected for this task.");
        } else if (requiredSuccess < requiredPlans) {
            missing.add("Required platform evidence is incomplete; check integration credentials, URLs, MinIO availability, and retryable tool failures.");
        }
        if (evidence == null || evidence.isEmpty()) {
            missing.add("Evidence Pack is empty; at least one grounded evidence item is required.");
        }
        if (findings != null) {
            findings.stream()
                    .flatMap(finding -> finding.missingEvidence().stream())
                    .distinct()
                    .forEach(missing::add);
        }
        return missing.stream().distinct().toList();
    }
}
