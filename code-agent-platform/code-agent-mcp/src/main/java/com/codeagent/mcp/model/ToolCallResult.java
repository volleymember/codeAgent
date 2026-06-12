package com.codeagent.mcp.model;

import com.codeagent.common.domain.EvidenceItem;

import java.util.List;

public record ToolCallResult(
        String toolName,
        String status,
        String summary,
        String rawRef,
        List<EvidenceItem> evidence,
        String errorMessage,
        long latencyMs,
        int rawTokens,
        int contextTokens,
        double compressionRatio,
        boolean sandboxed
) {
    public ToolCallResult(String toolName,
                          String status,
                          String summary,
                          String rawRef,
                          List<EvidenceItem> evidence,
                          String errorMessage,
                          long latencyMs) {
        this(toolName, status, summary, rawRef, evidence, errorMessage, latencyMs, 0, 0, 1.0, false);
    }

    public ToolCallResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        rawTokens = Math.max(0, rawTokens);
        contextTokens = Math.max(0, contextTokens);
        compressionRatio = Double.isNaN(compressionRatio) || Double.isInfinite(compressionRatio)
                ? 1.0
                : Math.max(0.0, compressionRatio);
    }
}
