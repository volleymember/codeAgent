package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "retrieval_log")
public class RetrievalLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "project_key", nullable = false)
    private String projectKey;

    @Column
    private String branch;

    @Column(name = "commit_id")
    private String commitId;

    @Column(name = "build_id")
    private String buildId;

    @Column(name = "evidence_types", columnDefinition = "JSON")
    private String evidenceTypes;

    @Column(name = "result_count", nullable = false)
    private Integer resultCount;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
