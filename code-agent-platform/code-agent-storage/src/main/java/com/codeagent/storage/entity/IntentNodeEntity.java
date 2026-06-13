package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "intent_node",
        uniqueConstraints = @UniqueConstraint(name = "uk_intent_node_code", columnNames = {"tree_code", "version", "node_code"}))
public class IntentNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tree_code", nullable = false)
    public String treeCode;

    @Column(nullable = false)
    public Integer version;

    @Column(name = "node_code", nullable = false)
    public String nodeCode;

    @Column(name = "parent_code")
    public String parentCode;

    @Column(name = "node_name", nullable = false)
    public String nodeName;

    @Column(name = "node_type", nullable = false)
    public String nodeType;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "keywords_json", columnDefinition = "JSON")
    public String keywordsJson;

    @Column(name = "example_queries_json", columnDefinition = "JSON")
    public String exampleQueriesJson;

    @Column(name = "default_time_range_hours")
    public Integer defaultTimeRangeHours;

    @Column(name = "allowed_tool_types_json", columnDefinition = "JSON")
    public String allowedToolTypesJson;

    @Column(name = "required_evidence_types_json", columnDefinition = "JSON")
    public String requiredEvidenceTypesJson;

    @Column
    public Boolean enabled = true;

    @Column(name = "sort_order")
    public Integer sortOrder = 0;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
