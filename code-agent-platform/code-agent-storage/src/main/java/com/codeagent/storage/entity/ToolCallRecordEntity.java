package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tool_call_record")
public class ToolCallRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_no", nullable = false)
    public String taskNo;

    @Column(name = "tool_name", nullable = false)
    public String toolName;

    @Column(name = "input_json", columnDefinition = "JSON")
    public String inputJson;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    public String outputSummary;

    @Column(name = "raw_output_uri")
    public String rawOutputUri;

    @Column
    public String status;

    @Column(name = "latency_ms")
    public Long latencyMs;

    @Column(name = "raw_tokens")
    public Long rawTokens;

    @Column(name = "context_tokens")
    public Long contextTokens;

    @Column(name = "compression_ratio")
    public Double compressionRatio;

    @Column(name = "sandboxed")
    public Boolean sandboxed;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "created_at")
    public LocalDateTime createdAt;
}
