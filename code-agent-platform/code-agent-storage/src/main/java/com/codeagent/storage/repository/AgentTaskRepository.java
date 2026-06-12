package com.codeagent.storage.repository;

import com.codeagent.storage.entity.AgentTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, Long> {
    Optional<AgentTaskEntity> findByTaskNo(String taskNo);

    List<AgentTaskEntity> findByStatusIn(List<String> statuses);
}
