package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;

import java.util.List;
import java.util.Map;

public record CompressedToolObservation(
        String toolName,
        String sourceSystem,
        String compressedText,
        Map<String, Object> extractedFacts,
        List<EvidenceItem> evidenceItems,
        int redactionCount,
        int droppedNoiseCount,
        int rawSize,
        int compressedSize
) {
    public CompressedToolObservation {
        extractedFacts = extractedFacts == null ? Map.of() : Map.copyOf(extractedFacts);
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
    }
}
