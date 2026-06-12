package com.codeagent.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_task")
public class AgentTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_no", nullable = false, unique = true)
    public String taskNo;

    @Column(name = "task_type", nullable = false)
    public String taskType;

    @Column(name = "project_key")
    public String projectKey;

    @Column(name = "user_input", columnDefinition = "TEXT")
    public String userInput;

    @Column(nullable = false)
    public String status;

    @Column(name = "current_round")
    public Integer currentRound = 0;

    @Column(name = "max_rounds")
    public Integer maxRounds = 3;

    @Column(name = "request_json", columnDefinition = "JSON")
    public String requestJson;

    @Column(name = "final_report", columnDefinition = "TEXT")
    public String finalReport;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
