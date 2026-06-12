package com.codeagent.storage.repository;

import com.codeagent.storage.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {
    List<DocumentEntity> findByProjectKeyOrderByIdDesc(String projectKey);

    Optional<DocumentEntity> findByDocId(String docId);
}
