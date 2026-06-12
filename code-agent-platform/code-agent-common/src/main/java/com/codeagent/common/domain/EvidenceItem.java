package com.codeagent.common.domain;

import java.util.Map;

public record EvidenceItem(
        String sourceType,
        String sourceSystem,
        String title,
        String summary,
        double score,
        String sourceUri,
        String sourceUrl,
        String filePath,
        String lineRange,
        String rawRef,
        String matchReason,
        Map<String, Object> metadata
) {
    public EvidenceItem {
        sourceSystem = present(sourceSystem, inferSourceSystem(sourceType));
        sourceUrl = present(sourceUrl, sourceUri);
        filePath = present(filePath, "N/A");
        lineRange = present(lineRange, "N/A");
        matchReason = present(matchReason, "N/A");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public EvidenceItem(String sourceType, String title, String summary, double score,
                        String sourceUri, String rawRef, Map<String, Object> metadata) {
        this(sourceType, inferSourceSystem(sourceType), title, summary, score,
                sourceUri, sourceUri, metadataValue(metadata, "filePath", "N/A"),
                metadataValue(metadata, "lineRange", "N/A"), rawRef,
                metadataValue(metadata, "matchReason", "tool evidence"), metadata);
    }

    private static String present(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null || fallback.isBlank() ? "N/A" : fallback;
    }

    private static String metadataValue(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null || !metadata.containsKey(key) || metadata.get(key) == null) {
            return fallback;
        }
        String value = String.valueOf(metadata.get(key));
        return value.isBlank() ? fallback : value;
    }

    private static String inferSourceSystem(String sourceType) {
        if (sourceType == null) {
            return "UNKNOWN";
        }
        String normalized = sourceType.toLowerCase();
        if (normalized.startsWith("gitlab")) {
            return "GITLAB";
        }
        if (normalized.startsWith("jenkins")) {
            return "JENKINS";
        }
        if (normalized.startsWith("sonar")) {
            return "SONARQUBE";
        }
        if (normalized.startsWith("doc") || normalized.startsWith("markdown")) {
            return "DOC";
        }
        return "UNKNOWN";
    }
}
