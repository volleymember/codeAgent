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
@Table(name = "intent_tree",
        uniqueConstraints = @UniqueConstraint(name = "uk_intent_tree_version", columnNames = {"tree_code", "version"}))
public class IntentTreeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tree_code", nullable = false)
    public String treeCode;

    @Column(name = "tree_name", nullable = false)
    public String treeName;

    @Column(nullable = false)
    public Integer version;

    @Column(nullable = false)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
