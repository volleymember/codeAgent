package com.codeagent.core.intent.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IntentNodeRequest(
        Integer version,
        @NotBlank String nodeCode,
        String parentCode,
        @NotBlank String nodeName,
        @NotBlank String nodeType,
        String description,
        List<String> keywords,
        List<String> exampleQueries,
        Integer defaultTimeRangeHours,
        List<String> allowedToolTypes,
        List<String> requiredEvidenceTypes,
        Boolean enabled,
        Integer sortOrder
) {
}
