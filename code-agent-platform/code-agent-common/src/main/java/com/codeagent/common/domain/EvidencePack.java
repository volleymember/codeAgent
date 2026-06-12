package com.codeagent.common.domain;

import java.util.List;

public record EvidencePack(
        String taskNo,
        String query,
        List<EvidenceItem> evidence
) {
}
