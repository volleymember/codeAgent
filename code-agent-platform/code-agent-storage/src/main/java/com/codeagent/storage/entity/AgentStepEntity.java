package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_step")
public class AgentStepEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_no", nullable = false)
    public String taskNo;

    @Column(name = "step_id", nullable = false)
    public String stepId;

    @Column(name = "agent_name", nullable = false)
    public String agentName;

    @Column(name = "tool_name")
    public String toolName;

    @Column(nullable = false)
    public String status;

    @Column(name = "input_json", columnDefinition = "JSON")
    public String inputJson;

    @Column(name = "output_summary", columnDefinition = "TEXT")
    public String outputSummary;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "started_at")
    public LocalDateTime startedAt;

    @Column(name = "finished_at")
    public LocalDateTime finishedAt;
}
