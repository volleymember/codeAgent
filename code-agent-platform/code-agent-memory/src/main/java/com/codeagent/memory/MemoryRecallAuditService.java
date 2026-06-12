package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryRecallRecordEntity;
import com.codeagent.storage.repository.MemoryRecallRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemoryRecallAuditService {
    private final MemoryRecallRecordRepository repository;

    public MemoryRecallAuditService(MemoryRecallRecordRepository repository) {
        this.repository = repository;
    }

    public MemoryRecallRecordEntity record(MemoryRecallRequest request, MemoryCenterContext context, long latencyMs) {
        MemoryRecallRecordEntity entity = new MemoryRecallRecordEntity();
        entity.taskNo = request.taskNo();
        entity.sessionId = request.sessionId();
        entity.projectKey = request.projectKey();
        entity.taskType = request.taskType();
        entity.agentName = request.agentName();
        entity.phase = request.phase();
        entity.queryText = request.query();
        entity.coreRuleCount = context.coreRules().size();
        entity.episodeCount = context.recalledEpisodes().size();
        entity.recalledEpisodeIds = JsonSupport.toJson(context.recalledEpisodes().stream()
                .map(MemoryEpisodeMatch::episodeId)
                .toList());
        entity.maxScore = context.recalledEpisodes().stream()
                .mapToDouble(MemoryEpisodeMatch::score)
                .max()
                .orElse(0.0);
        entity.latencyMs = latencyMs;
        entity.createdAt = LocalDateTime.now();
        return repository.save(entity);
    }

    public List<MemoryRecallRecordEntity> byProject(String projectKey) {
        return repository.findTop100ByProjectKeyOrderByIdDesc(projectKey);
    }

    public List<MemoryRecallRecordEntity> byTask(String taskNo) {
        return repository.findByTaskNoOrderByIdDesc(taskNo);
    }

    public List<MemoryRecallRecordEntity> bySession(String sessionId) {
        return repository.findBySessionIdOrderByIdDesc(sessionId);
    }
}
