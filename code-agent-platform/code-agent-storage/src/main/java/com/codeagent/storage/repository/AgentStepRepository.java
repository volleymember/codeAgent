package com.codeagent.storage.repository;

import com.codeagent.storage.entity.AgentStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentStepRepository extends JpaRepository<AgentStepEntity, Long> {
    List<AgentStepEntity> findByTaskNoOrderByIdAsc(String taskNo);
}
