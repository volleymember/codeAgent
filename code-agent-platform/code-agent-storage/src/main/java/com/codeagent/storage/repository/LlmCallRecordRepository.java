package com.codeagent.storage.repository;

import com.codeagent.storage.entity.LlmCallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmCallRecordRepository extends JpaRepository<LlmCallRecordEntity, Long> {
}
