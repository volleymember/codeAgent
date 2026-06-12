package com.codeagent.storage.repository;

import com.codeagent.storage.entity.ToolCallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolCallRecordRepository extends JpaRepository<ToolCallRecordEntity, Long> {
    List<ToolCallRecordEntity> findByTaskNoOrderByIdAsc(String taskNo);
}
