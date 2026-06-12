package com.codeagent.common.domain;

import java.util.List;

public record EvidencePackage(
        String projectKey,
        String query,
        int resultCount,
        List<EvidenceItem> evidenceItems,
        List<Citation> citations
) {
    public EvidencePackage {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
