package com.codeagent.storage.repository;

import com.codeagent.storage.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long>,
        JpaSpecificationExecutor<DocumentChunkEntity>, DocumentChunkSearchRepository {
    List<DocumentChunkEntity> findByProjectKeyOrderByIdDesc(String projectKey);

    Optional<DocumentChunkEntity> findByChunkId(String chunkId);

    List<DocumentChunkEntity> findByVectorIdIn(List<String> vectorIds);

    Optional<DocumentChunkEntity> findFirstByContentHashOrderByIdDesc(String contentHash);

    List<DocumentChunkEntity> findByEvidenceNoOrderByChunkIndexAsc(String evidenceNo);

    void deleteByEvidenceNo(String evidenceNo);
}
