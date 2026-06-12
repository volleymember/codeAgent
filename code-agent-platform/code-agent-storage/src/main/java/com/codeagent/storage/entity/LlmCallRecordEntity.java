package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_call_record")
public class LlmCallRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_no", nullable = false)
    public String taskNo;

    @Column(name = "session_id")
    public String sessionId;

    @Column
    public String model;

    @Column(name = "task_type")
    public String taskType;

    @Column(name = "input_tokens")
    public Long inputTokens;

    @Column(name = "output_tokens")
    public Long outputTokens;

    @Column(name = "estimated_input_tokens")
    public Long estimatedInputTokens;

    @Column(name = "max_input_tokens")
    public Long maxInputTokens;

    @Column(name = "budget_policy")
    public String budgetPolicy;

    @Column(name = "latency_ms")
    public Long latencyMs;

    @Column
    public String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
