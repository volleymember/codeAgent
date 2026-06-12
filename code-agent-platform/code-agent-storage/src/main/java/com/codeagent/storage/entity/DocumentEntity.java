package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "document")
public class DocumentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "doc_id", nullable = false, unique = true)
    public String docId;

    @Column(name = "project_key")
    public String projectKey;

    @Column
    public String title;

    @Column(name = "doc_type")
    public String docType;

    @Column(name = "source_type")
    public String sourceType;

    @Column(name = "source_uri")
    public String sourceUri;

    @Column(name = "raw_ref")
    public String rawRef;

    @Column
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
