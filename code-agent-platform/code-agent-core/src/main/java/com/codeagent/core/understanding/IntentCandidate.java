package com.codeagent.core.understanding;

import java.util.List;

public record IntentCandidate(
        String nodeCode,
        double confidence,
        List<String> matchedSignals
) {
    public IntentCandidate {
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
    }
}
