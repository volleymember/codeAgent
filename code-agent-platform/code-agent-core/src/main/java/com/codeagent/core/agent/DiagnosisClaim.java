package com.codeagent.core.agent;

import java.util.List;

public record DiagnosisClaim(
        String claim,
        List<String> evidenceRefs,
        double confidence,
        List<String> counterEvidence
) {
    public DiagnosisClaim {
        claim = claim == null || claim.isBlank() ? "N/A" : claim;
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        counterEvidence = counterEvidence == null ? List.of() : List.copyOf(counterEvidence);
    }
}
