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
        List<String> preferredDiscoveryTools,
        List<String> preferredAnalysisTools,
        List<String> requiredConfigFields,
        Boolean enabled,
        Integer sortOrder
) {
    public IntentNodeRequest(Integer version,
                             String nodeCode,
                             String parentCode,
                             String nodeName,
                             String nodeType,
                             String description,
                             List<String> keywords,
                             List<String> exampleQueries,
                             Integer defaultTimeRangeHours,
                             List<String> allowedToolTypes,
                             List<String> requiredEvidenceTypes,
                             Boolean enabled,
                             Integer sortOrder) {
        this(version, nodeCode, parentCode, nodeName, nodeType, description, keywords, exampleQueries,
                defaultTimeRangeHours, allowedToolTypes, requiredEvidenceTypes, List.of(), List.of(), List.of(),
                enabled, sortOrder);
    }
}
