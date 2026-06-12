package com.codeagent.storage.repository;

import com.codeagent.storage.entity.EvidenceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EvidenceRecordRepository extends JpaRepository<EvidenceRecordEntity, Long> {
    List<EvidenceRecordEntity> findByTaskNoOrderByIdAsc(String taskNo);

    Optional<EvidenceRecordEntity> findByEvidenceNo(String evidenceNo);

    Optional<EvidenceRecordEntity> findFirstByContentHashOrderByIdDesc(String contentHash);

    @Query("""
            select e from EvidenceRecordEntity e
            where e.projectKey = :projectKey
              and ((:branch is null and e.branch is null) or e.branch = :branch)
              and ((:commitId is null and e.commitId is null) or e.commitId = :commitId)
              and ((:buildId is null and e.buildId is null) or e.buildId = :buildId)
              and e.evidenceType = :evidenceType
              and e.sourceSystem = :sourceSystem
              and ((:sourceUrl is null and e.sourceUrl is null) or e.sourceUrl = :sourceUrl)
              and ((:filePath is null and e.filePath is null) or e.filePath = :filePath)
            order by e.id desc
            """)
    List<EvidenceRecordEntity> findLogicalEvidence(@Param("projectKey") String projectKey,
                                                   @Param("branch") String branch,
                                                   @Param("commitId") String commitId,
                                                   @Param("buildId") String buildId,
                                                   @Param("evidenceType") String evidenceType,
                                                   @Param("sourceSystem") String sourceSystem,
                                                   @Param("sourceUrl") String sourceUrl,
                                                   @Param("filePath") String filePath);
}
