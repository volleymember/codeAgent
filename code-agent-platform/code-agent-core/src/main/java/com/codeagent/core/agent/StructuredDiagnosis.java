package com.codeagent.core.agent;

import java.util.List;

public record StructuredDiagnosis(
        String suspectedCause,
        double confidence,
        List<DiagnosisClaim> claims,
        String suggestedFix,
        List<String> suggestedTests,
        List<String> toolSummary,
        List<String> uncertainties
) {
    public StructuredDiagnosis {
        suspectedCause = present(suspectedCause, "N/A");
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        claims = claims == null ? List.of() : List.copyOf(claims);
        suggestedFix = present(suggestedFix, "N/A");
        suggestedTests = suggestedTests == null ? List.of() : List.copyOf(suggestedTests);
        toolSummary = toolSummary == null ? List.of() : List.copyOf(toolSummary);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }

    private static String present(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
