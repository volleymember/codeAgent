package com.codeagent.core.intent.dto;

import java.util.List;

public record IntentLeafView(
        String treeCode,
        Integer version,
        String nodeCode,
        String nodePath,
        String nodeName,
        String description,
        List<String> keywords,
        List<String> exampleQueries,
        Integer defaultTimeRangeHours,
        List<String> allowedToolTypes,
        List<String> requiredEvidenceTypes
) {
    public IntentLeafView {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        exampleQueries = exampleQueries == null ? List.of() : List.copyOf(exampleQueries);
        allowedToolTypes = allowedToolTypes == null ? List.of() : List.copyOf(allowedToolTypes);
        requiredEvidenceTypes = requiredEvidenceTypes == null ? List.of() : List.copyOf(requiredEvidenceTypes);
    }
}
