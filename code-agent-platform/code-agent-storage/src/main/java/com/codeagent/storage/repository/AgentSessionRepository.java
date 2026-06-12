package com.codeagent.storage.repository;

import com.codeagent.storage.entity.AgentSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, Long> {
    Optional<AgentSessionEntity> findBySessionId(String sessionId);

    List<AgentSessionEntity> findByTaskNo(String taskNo);
}
