package com.codeagent.mcp.tool;

import com.codeagent.common.domain.EvidenceItem;

import java.util.List;

public record ToolExecutionPayload(
        Object rawPayload,
        String summary,
        List<EvidenceItem> evidence
) {
}
