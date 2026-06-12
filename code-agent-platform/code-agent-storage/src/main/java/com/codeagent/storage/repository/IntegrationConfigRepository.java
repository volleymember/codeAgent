package com.codeagent.storage.repository;

import com.codeagent.storage.entity.IntegrationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfigEntity, Long> {
    List<IntegrationConfigEntity> findByPlatformIgnoreCaseOrderByIdDesc(String platform);
}
