package com.codeagent.mcp.sandbox;

import com.codeagent.common.domain.EvidenceItem;

import java.util.List;
import java.util.Map;

public record SandboxedToolPayload(
        DataArtifactType artifactType,
        String summary,
        List<EvidenceItem> evidence,
        Map<String, Object> structuredFacts,
        int rawTokens,
        int contextTokens,
        double compressionRatio
) {
    public SandboxedToolPayload {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        structuredFacts = structuredFacts == null ? Map.of() : Map.copyOf(structuredFacts);
        rawTokens = Math.max(0, rawTokens);
        contextTokens = Math.max(0, contextTokens);
        compressionRatio = Double.isNaN(compressionRatio) || Double.isInfinite(compressionRatio)
                ? 1.0
                : Math.max(0.0, compressionRatio);
    }
}
