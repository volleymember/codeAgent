package com.codeagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceChunk {
    private String chunkId;
    private String evidenceId;
    private String projectKey;
    private String branch;
    private String commitId;
    private String buildId;
    private EvidenceType evidenceType;
    private SourceSystem sourceSystem;
    private String sourceUrl;
    private String filePath;
    private String symbolName;
    private String title;
    private String summary;
    private List<String> keywords;
    private String content;
    private int lineStart;
    private int lineEnd;
    private String lineRange;
    private int chunkIndex;
    private String contentHash;
    private String vectorId;
    private double denseScore;
    private Map<String, Object> metadata;
}
