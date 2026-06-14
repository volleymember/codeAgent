package com.codeagent.core.react;

import java.util.List;

public record EvidenceMatrix(
        List<String> requiredEvidenceTypes,
        List<String> preferredDiscoveryTools,
        List<String> preferredAnalysisTools,
        List<String> requiredConfigFields,
        List<String> missingRuntimeFacts
) {
    public EvidenceMatrix {
        requiredEvidenceTypes = requiredEvidenceTypes == null ? List.of() : List.copyOf(requiredEvidenceTypes);
        preferredDiscoveryTools = preferredDiscoveryTools == null ? List.of() : List.copyOf(preferredDiscoveryTools);
        preferredAnalysisTools = preferredAnalysisTools == null ? List.of() : List.copyOf(preferredAnalysisTools);
        requiredConfigFields = requiredConfigFields == null ? List.of() : List.copyOf(requiredConfigFields);
        missingRuntimeFacts = missingRuntimeFacts == null ? List.of() : List.copyOf(missingRuntimeFacts);
    }
}
