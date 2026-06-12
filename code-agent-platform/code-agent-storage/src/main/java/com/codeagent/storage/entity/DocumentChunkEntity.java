package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunk")
public class DocumentChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "chunk_id", nullable = false, unique = true)
    public String chunkId;

    @Column(name = "doc_id", nullable = false)
    public String docId;

    @Column(name = "project_key")
    public String projectKey;

    @Column
    public String branch;

    @Column(name = "commit_id")
    public String commitId;

    @Column(name = "build_id")
    public String buildId;

    @Column(name = "module_name")
    public String moduleName;

    @Column(name = "evidence_no")
    public String evidenceNo;

    @Column(name = "evidence_type")
    public String evidenceType;

    @Column(name = "source_system")
    public String sourceSystem;

    @Column(name = "doc_type")
    public String docType;

    @Column(name = "source_type")
    public String sourceType;

    @Column(name = "source_uri")
    public String sourceUri;

    @Column(name = "source_url")
    public String sourceUrl;

    @Column(name = "file_path")
    public String filePath;

    @Column(name = "symbol_name")
    public String symbolName;

    @Column(name = "line_start")
    public Integer lineStart;

    @Column(name = "line_end")
    public Integer lineEnd;

    @Column(name = "line_range")
    public String lineRange;

    @Column(name = "chunk_index")
    public Integer chunkIndex;

    @Column(name = "content_hash")
    public String contentHash;

    @Column
    public String title;

    @Column(columnDefinition = "TEXT")
    public String keywords;

    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(columnDefinition = "JSON")
    public String metadata;

    @Column(name = "vector_id")
    public String vectorId;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
