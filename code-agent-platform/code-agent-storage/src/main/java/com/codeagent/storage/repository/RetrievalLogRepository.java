package com.codeagent.storage.repository;

import com.codeagent.storage.entity.RetrievalLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrievalLogRepository extends JpaRepository<RetrievalLogEntity, Long> {
}
