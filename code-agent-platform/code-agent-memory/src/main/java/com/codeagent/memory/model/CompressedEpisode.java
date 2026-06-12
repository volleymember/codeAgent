package com.codeagent.memory.model;

import java.util.List;

public record CompressedEpisode(
        String episodeId,
        List<String> symptoms,
        String rootCause,
        String fixContent,
        String reliability,
        double score,
        String matchReason,
        String sourceUri
) {
    public CompressedEpisode {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
    }
}
