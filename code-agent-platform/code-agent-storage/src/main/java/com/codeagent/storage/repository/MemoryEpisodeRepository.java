package com.codeagent.storage.repository;

import com.codeagent.storage.entity.MemoryEpisodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryEpisodeRepository extends JpaRepository<MemoryEpisodeEntity, Long> {
    List<MemoryEpisodeEntity> findByProjectKeyOrderByIdDesc(String projectKey);

    List<MemoryEpisodeEntity> findTop100ByProjectKeyInOrderByUpdatedAtDesc(List<String> projectKeys);

    Optional<MemoryEpisodeEntity> findFirstByProjectKeyAndSymptomSignatureOrderByIdDesc(String projectKey, String symptomSignature);

    Optional<MemoryEpisodeEntity> findByEpisodeId(String episodeId);

    long countByProjectKey(String projectKey);
}
