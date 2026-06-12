package com.codeagent.storage.evidence;

public record StoredEvidenceObject(
        String objectId,
        long fileSize,
        String contentHash
) {
}
