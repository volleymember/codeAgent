package com.codeagent.storage.repository;

import com.codeagent.storage.entity.MemoryRecallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryRecallRecordRepository extends JpaRepository<MemoryRecallRecordEntity, Long> {
    List<MemoryRecallRecordEntity> findTop100ByProjectKeyOrderByIdDesc(String projectKey);

    List<MemoryRecallRecordEntity> findByTaskNoOrderByIdDesc(String taskNo);

    List<MemoryRecallRecordEntity> findBySessionIdOrderByIdDesc(String sessionId);
}
