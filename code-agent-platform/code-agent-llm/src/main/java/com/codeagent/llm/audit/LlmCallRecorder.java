package com.codeagent.llm.audit;

import com.codeagent.storage.entity.LlmCallRecordEntity;
import com.codeagent.storage.repository.LlmCallRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LlmCallRecorder {
    private final LlmCallRecordRepository repository;

    public LlmCallRecorder(LlmCallRecordRepository repository) {
        this.repository = repository;
    }

    public void record(String taskNo, String sessionId, String model, String taskType,
                       long inputTokens, long outputTokens, long latencyMs,
                       String status, String errorMessage) {
        record(taskNo, sessionId, model, taskType, inputTokens, outputTokens, 0, 0,
                "UNKNOWN", latencyMs, status, errorMessage);
    }

    public void record(String taskNo, String sessionId, String model, String taskType,
                       long inputTokens, long outputTokens, long estimatedInputTokens,
                       long maxInputTokens, String budgetPolicy, long latencyMs,
                       String status, String errorMessage) {
        LlmCallRecordEntity entity = new LlmCallRecordEntity();
        entity.taskNo = taskNo == null ? "UNKNOWN" : taskNo;
        entity.sessionId = sessionId;
        entity.model = model;
        entity.taskType = taskType;
        entity.inputTokens = inputTokens;
        entity.outputTokens = outputTokens;
        entity.estimatedInputTokens = estimatedInputTokens;
        entity.maxInputTokens = maxInputTokens;
        entity.budgetPolicy = budgetPolicy;
        entity.latencyMs = latencyMs;
        entity.status = status;
        entity.errorMessage = errorMessage;
        entity.createdAt = LocalDateTime.now();
        repository.save(entity);
    }
}
