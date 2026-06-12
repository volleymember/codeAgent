package com.codeagent.core.context;

import java.util.List;
import java.util.Map;

public record ManagedEvidenceContext(
        int originalEvidenceCount,
        int includedEvidenceCount,
        int omittedEvidenceCount,
        int estimatedTokens,
        int maxEvidenceTokens,
        String budgetPolicy,
        List<Map<String, Object>> evidence
) {
    public ManagedEvidenceContext {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
