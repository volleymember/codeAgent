package com.codeagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Evidence {
    private String evidenceId;
    private String taskNo;
    private String projectKey;
    private String branch;
    private String commitId;
    private String buildId;
    private EvidenceType evidenceType;
    private SourceSystem sourceSystem;
    private String sourceUrl;
    private String filePath;
    private String symbolName;
    private String contentHash;
    private String objectId;
    private Long fileSize;
    private String title;
    private String summary;
    private List<String> keywords;
    private String content;
    private String rawRef;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
