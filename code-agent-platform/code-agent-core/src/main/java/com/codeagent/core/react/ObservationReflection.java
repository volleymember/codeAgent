package com.codeagent.core.react;

import java.util.List;

public record ObservationReflection(
        boolean sufficient,
        double confidence,
        List<String> missingEvidence,
        List<String> nextToolHints,
        String finalReasoningSummary
) {
    public ObservationReflection {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        nextToolHints = nextToolHints == null ? List.of() : List.copyOf(nextToolHints);
    }
}
