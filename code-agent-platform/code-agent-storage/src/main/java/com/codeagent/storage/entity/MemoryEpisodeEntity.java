package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "memory_episode")
public class MemoryEpisodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "episode_id", nullable = false, unique = true)
    public String episodeId;

    @Column(name = "project_key")
    public String projectKey;

    @Column(name = "symptoms_json", columnDefinition = "JSON")
    public String symptomsJson;

    @Column(name = "symptom_signature")
    public String symptomSignature;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    public String rootCause;

    @Column(name = "fix_content", columnDefinition = "TEXT")
    public String fixContent;

    @Column(name = "verified_by", columnDefinition = "TEXT")
    public String verifiedBy;

    @Column(name = "source_uri")
    public String sourceUri;

    @Column
    public String reliability;

    @Column(name = "tags_json", columnDefinition = "JSON")
    public String tagsJson;

    @Column(name = "confidence_score")
    public Double confidenceScore;

    @Column(name = "hit_count")
    public Integer hitCount;

    @Column(name = "helpful_count")
    public Integer helpfulCount;

    @Column(name = "unhelpful_count")
    public Integer unhelpfulCount;

    @Column
    public String status;

    @Column(name = "feedback_json", columnDefinition = "JSON")
    public String feedbackJson;

    @Column(name = "last_recalled_at")
    public LocalDateTime lastRecalledAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
