package com.codeagent.core.understanding;

import java.util.List;
import java.util.Map;

public record IntentClassificationResult(
        String selectedIntentCode,
        String selectedIntentPath,
        double confidence,
        List<IntentCandidate> topCandidates,
        List<String> matchedSignals,
        boolean ambiguity,
        String clarificationQuestion,
        Map<String, Object> extractedFacts
) {
    public IntentClassificationResult {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        topCandidates = topCandidates == null ? List.of() : List.copyOf(topCandidates);
        matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        extractedFacts = extractedFacts == null ? Map.of() : Map.copyOf(extractedFacts);
    }
}
