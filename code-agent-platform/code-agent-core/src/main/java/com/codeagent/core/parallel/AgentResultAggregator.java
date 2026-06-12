package com.codeagent.core.parallel;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.model.ToolCallResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AgentResultAggregator {
    public ParallelAgentExecutionReport aggregate(String taskNo,
                                                  int submittedCount,
                                                  long latencyMs,
                                                  List<AgentExecutionResult> results) {
        List<AgentExecutionResult> safeResults = results == null ? List.of() : results;
        int successfulCount = (int) safeResults.stream().filter(AgentExecutionResult::success).count();
        int failedCount = safeResults.size() - successfulCount;
        int requiredFailureCount = (int) safeResults.stream().filter(AgentExecutionResult::requiredFailure).count();
        List<ToolCallResult> toolResults = safeResults.stream()
                .map(AgentExecutionResult::toolResult)
                .filter(result -> result != null)
                .toList();
        List<EvidenceItem> evidence = deduplicateEvidence(safeResults);
        Map<String, Long> byWorkType = safeResults.stream()
                .collect(LinkedHashMap::new,
                        (map, result) -> map.merge(result.workItem().workType().name(), 1L, Long::sum),
                        LinkedHashMap::putAll);
        Map<String, Object> stats = new LinkedHashMap<>();
        List<AgentFinding> findings = safeResults.stream()
                .map(this::finding)
                .toList();
        stats.put("submittedCount", submittedCount);
        stats.put("successfulCount", successfulCount);
        stats.put("failedCount", failedCount);
        stats.put("requiredFailureCount", requiredFailureCount);
        stats.put("evidenceCount", evidence.size());
        stats.put("latencyMs", latencyMs);
        stats.put("byWorkType", byWorkType);
        stats.put("agentFindingCount", findings.size());
        return new ParallelAgentExecutionReport(taskNo, submittedCount, successfulCount, failedCount,
                requiredFailureCount, latencyMs, safeResults, toolResults, evidence, findings, stats);
    }

    private List<EvidenceItem> deduplicateEvidence(List<AgentExecutionResult> results) {
        Map<String, EvidenceItem> deduped = new LinkedHashMap<>();
        for (AgentExecutionResult result : results) {
            for (EvidenceItem item : result.evidence()) {
                String key = evidenceKey(item);
                EvidenceItem existing = deduped.get(key);
                if (existing == null || item.score() > existing.score()) {
                    deduped.put(key, item);
                }
            }
        }
        return deduped.values().stream()
                .sorted(Comparator.comparingDouble(EvidenceItem::score).reversed())
                .toList();
    }

    private String evidenceKey(EvidenceItem item) {
        return "%s|%s|%s|%s|%s".formatted(
                value(item.sourceSystem()),
                value(item.sourceUrl()),
                value(item.filePath()),
                value(item.lineRange()),
                value(item.title())
        );
    }

    private AgentFinding finding(AgentExecutionResult result) {
        List<EvidenceItem> evidence = result.evidence();
        List<String> evidenceRefs = evidence.stream()
                .map(this::evidenceRef)
                .filter(ref -> !ref.isBlank())
                .distinct()
                .limit(8)
                .toList();
        double confidence = result.success()
                ? evidence.stream().mapToDouble(EvidenceItem::score).average().orElse(0.55)
                : 0.0;
        String claim = result.success()
                ? claim(result, evidence)
                : "Failed to collect %s evidence: %s".formatted(result.workItem().workType().name(), value(result.errorMessage()));
        List<String> missing = result.requiredFailure()
                ? List.of("Required evidence branch failed: " + result.workItem().toolName())
                : List.of();
        return new AgentFinding(
                result.workItem().agentName(),
                result.workItem().workType(),
                result.toolResult() == null ? "FAILED" : result.toolResult().status(),
                claim,
                confidence,
                evidenceRefs,
                missing,
                Map.of(
                        "stepId", result.workItem().stepId(),
                        "toolName", result.workItem().toolName(),
                        "source", result.workItem().source().name(),
                        "attempts", result.attempts(),
                        "latencyMs", result.latencyMs()
                )
        );
    }

    private String claim(AgentExecutionResult result, List<EvidenceItem> evidence) {
        if (evidence.isEmpty()) {
            return "%s completed but returned no evidence.".formatted(result.workItem().toolName());
        }
        return evidence.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(EvidenceItem::score).reversed())
                .findFirst()
                .map(item -> "%s found %s: %s".formatted(
                        result.workItem().agentName(),
                        value(item.title()),
                        value(item.summary())))
                .orElse("%s completed.".formatted(result.workItem().toolName()));
    }

    private String evidenceRef(EvidenceItem item) {
        if (item.rawRef() != null && !item.rawRef().isBlank()) {
            return item.rawRef();
        }
        if (item.sourceUri() != null && !item.sourceUri().isBlank()) {
            return item.sourceUri();
        }
        return value(item.title());
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
