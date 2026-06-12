package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_record")
public class EvidenceRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "evidence_no", nullable = false, unique = true)
    public String evidenceNo;

    @Column(name = "task_no", nullable = false)
    public String taskNo;

    @Column(name = "project_key")
    public String projectKey;

    @Column
    public String branch;

    @Column(name = "commit_id")
    public String commitId;

    @Column(name = "build_id")
    public String buildId;

    @Column(name = "evidence_type")
    public String evidenceType;

    @Column(name = "source_system")
    public String sourceSystem;

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

    @Column(name = "content_hash")
    public String contentHash;

    @Column(name = "object_id")
    public String objectId;

    @Column(name = "file_size")
    public Long fileSize;

    @Column(name = "raw_ref")
    public String rawRef;

    @Column
    public String title;

    @Column(columnDefinition = "TEXT")
    public String summary;

    @Column
    public Double score;

    @Column(columnDefinition = "JSON")
    public String metadata;

    @Column(columnDefinition = "JSON")
    public String keywords;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
