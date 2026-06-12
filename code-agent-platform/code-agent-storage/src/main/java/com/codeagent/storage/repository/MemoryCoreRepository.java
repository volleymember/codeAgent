package com.codeagent.storage.repository;

import com.codeagent.storage.entity.MemoryCoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCoreRepository extends JpaRepository<MemoryCoreEntity, Long> {
    List<MemoryCoreEntity> findByProjectKeyOrderByPriorityDescIdDesc(String projectKey);

    List<MemoryCoreEntity> findByProjectKeyInAndStatusOrderByPriorityDescIdDesc(List<String> projectKeys, String status);

    long countByProjectKey(String projectKey);
}
