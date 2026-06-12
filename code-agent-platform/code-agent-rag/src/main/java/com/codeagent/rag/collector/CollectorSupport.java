package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.IndexEvidenceRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

abstract class CollectorSupport {
    protected String nextEvidenceId() {
        return "EVID-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
    }

    protected Map<String, Object> metadata(IndexEvidenceRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }
        put(metadata, "branch", request.getBranch());
        put(metadata, "commitId", request.getCommitId());
        put(metadata, "buildId", request.getBuildId());
        put(metadata, "rawRef", request.getRawRef());
        return metadata;
    }

    protected void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            metadata.put(key, value);
        }
    }

    protected String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
