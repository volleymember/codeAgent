package com.codeagent.common.domain;

public record Citation(
        String sourceSystem,
        String sourceUrl,
        String filePath,
        String lineRange
) {
    public Citation {
        sourceSystem = present(sourceSystem);
        sourceUrl = present(sourceUrl);
        filePath = present(filePath);
        lineRange = present(lineRange);
    }

    private static String present(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
