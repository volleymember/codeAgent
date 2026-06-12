package com.codeagent.memory.model;

import java.util.List;

public record MemoryEpisodeMatch(
        String episodeId,
        String projectKey,
        List<String> symptoms,
        String rootCause,
        String fixContent,
        String verifiedBy,
        String sourceUri,
        String reliability,
        double score,
        String matchReason
) {
    public MemoryEpisodeMatch {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        score = Math.max(0.0, Math.min(1.0, score));
    }
}
