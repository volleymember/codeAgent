package com.codeagent.rag.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class IndexEvidenceRequest {
    private String taskNo;

    @NotBlank
    private String projectKey;

    private String branch;
    private String commitId;
    private String buildId;

    @NotNull
    private SourceSystem sourceSystem;

    @NotNull
    private EvidenceType evidenceType;

    private String gitlabMrUrl;
    private String jenkinsBuildUrl;
    private String sonarProjectKey;
    private String docId;
    private String title;
    private String sourceUrl;
    private String filePath;
    private String symbolName;
    private String rawRef;
    private String summary;
    private String content;
    private List<String> keywords;
    private Map<String, Object> metadata;
}
