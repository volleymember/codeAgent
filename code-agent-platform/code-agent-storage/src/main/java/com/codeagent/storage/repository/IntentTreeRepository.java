package com.codeagent.storage.repository;

import com.codeagent.storage.entity.IntentTreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntentTreeRepository extends JpaRepository<IntentTreeEntity, Long> {
    Optional<IntentTreeEntity> findByTreeCodeAndVersion(String treeCode, Integer version);

    List<IntentTreeEntity> findByTreeCodeAndStatus(String treeCode, String status);

    List<IntentTreeEntity> findByStatusOrderByUpdatedAtDesc(String status);

    List<IntentTreeEntity> findByTreeCodeOrderByVersionDesc(String treeCode);
}
