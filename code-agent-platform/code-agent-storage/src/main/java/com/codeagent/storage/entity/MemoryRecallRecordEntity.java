package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "memory_recall_record")
public class MemoryRecallRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_no")
    public String taskNo;

    @Column(name = "session_id")
    public String sessionId;

    @Column(name = "project_key", nullable = false)
    public String projectKey;

    @Column(name = "task_type")
    public String taskType;

    @Column(name = "agent_name")
    public String agentName;

    @Column
    public String phase;

    @Column(name = "query_text", columnDefinition = "TEXT")
    public String queryText;

    @Column(name = "core_rule_count")
    public Integer coreRuleCount;

    @Column(name = "episode_count")
    public Integer episodeCount;

    @Column(name = "recalled_episode_ids", columnDefinition = "JSON")
    public String recalledEpisodeIds;

    @Column(name = "max_score")
    public Double maxScore;

    @Column(name = "latency_ms")
    public Long latencyMs;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
