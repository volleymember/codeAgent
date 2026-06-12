package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "memory_core")
public class MemoryCoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "project_key")
    public String projectKey;

    @Column
    public String type;

    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(name = "tags_json", columnDefinition = "JSON")
    public String tagsJson;

    @Column
    public String status;

    @Column(name = "source_uri")
    public String sourceUri;

    @Column
    public Integer priority;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
