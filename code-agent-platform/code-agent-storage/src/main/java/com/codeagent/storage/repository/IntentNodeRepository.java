package com.codeagent.storage.repository;

import com.codeagent.storage.entity.IntentNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntentNodeRepository extends JpaRepository<IntentNodeEntity, Long> {
    List<IntentNodeEntity> findByTreeCodeAndVersionOrderBySortOrderAscIdAsc(String treeCode, Integer version);

    Optional<IntentNodeEntity> findByTreeCodeAndVersionAndNodeCode(String treeCode, Integer version, String nodeCode);

    boolean existsByTreeCodeAndVersionAndNodeTypeAndEnabledTrue(String treeCode, Integer version, String nodeType);

    List<IntentNodeEntity> findByTreeCodeAndVersionAndNodeTypeAndEnabledTrueOrderBySortOrderAscIdAsc(
            String treeCode, Integer version, String nodeType);
}
