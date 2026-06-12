package com.codeagent.rag.store;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.storage.entity.DocumentChunkEntity;
import com.codeagent.storage.entity.DocumentEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import com.codeagent.storage.entity.RetrievalLogEntity;
import com.codeagent.storage.repository.DocumentChunkRepository;
import com.codeagent.storage.repository.DocumentRepository;
import com.codeagent.storage.repository.EvidenceRecordRepository;
import com.codeagent.storage.repository.RetrievalLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class EvidenceStore {
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final RetrievalLogRepository retrievalLogRepository;

    public EvidenceStore(EvidenceRecordRepository evidenceRecordRepository,
                         DocumentRepository documentRepository,
                         DocumentChunkRepository documentChunkRepository,
                         RetrievalLogRepository retrievalLogRepository) {
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.retrievalLogRepository = retrievalLogRepository;
    }

    public EvidenceRecordEntity saveEvidence(Evidence evidence) {
        EvidenceRecordEntity entity = evidenceRecordRepository.findByEvidenceNo(evidence.getEvidenceId())
                .orElseGet(EvidenceRecordEntity::new);
        entity.evidenceNo = evidence.getEvidenceId();
        entity.taskNo = evidence.getTaskNo() == null || evidence.getTaskNo().isBlank() ? "RAG" : evidence.getTaskNo();
        entity.projectKey = evidence.getProjectKey();
        entity.branch = evidence.getBranch();
        entity.commitId = evidence.getCommitId();
        entity.buildId = evidence.getBuildId();
        entity.evidenceType = evidence.getEvidenceType() == null ? null : evidence.getEvidenceType().name();
        entity.sourceSystem = evidence.getSourceSystem() == null ? null : evidence.getSourceSystem().name();
        entity.sourceType = entity.evidenceType;
        entity.sourceUri = evidence.getSourceUrl();
        entity.sourceUrl = evidence.getSourceUrl();
        entity.filePath = evidence.getFilePath();
        entity.symbolName = evidence.getSymbolName();
        entity.contentHash = evidence.getContentHash();
        entity.objectId = evidence.getObjectId();
        entity.fileSize = evidence.getFileSize();
        entity.rawRef = evidence.getRawRef();
        entity.title = evidence.getTitle();
        entity.summary = evidence.getSummary();
        entity.score = 1.0;
        entity.metadata = JsonSupport.toJson(evidence.getMetadata() == null ? Map.of() : evidence.getMetadata());
        entity.keywords = JsonSupport.toJson(evidence.getKeywords() == null ? List.of() : evidence.getKeywords());
        entity.createdAt = evidence.getCreatedAt() == null ? LocalDateTime.now() : evidence.getCreatedAt();
        return evidenceRecordRepository.save(entity);
    }

    public void saveDocument(Evidence evidence) {
        DocumentEntity entity = documentRepository.findByDocId(evidence.getEvidenceId())
                .orElseGet(DocumentEntity::new);
        entity.docId = evidence.getEvidenceId();
        entity.projectKey = evidence.getProjectKey();
        entity.title = evidence.getTitle();
        entity.docType = evidence.getEvidenceType() == null ? null : evidence.getEvidenceType().name();
        entity.sourceType = evidence.getSourceSystem() == null ? null : evidence.getSourceSystem().name();
        entity.sourceUri = evidence.getSourceUrl();
        entity.rawRef = evidence.getRawRef();
        entity.status = "INDEXED";
        if (entity.createdAt == null) {
            entity.createdAt = LocalDateTime.now();
        }
        entity.updatedAt = LocalDateTime.now();
        documentRepository.save(entity);
    }

    public DocumentChunkEntity saveChunk(EvidenceChunk chunk) {
        DocumentChunkEntity entity = new DocumentChunkEntity();
        entity.chunkId = chunk.getChunkId();
        entity.docId = chunk.getEvidenceId();
        entity.projectKey = chunk.getProjectKey();
        entity.branch = chunk.getBranch();
        entity.commitId = chunk.getCommitId();
        entity.buildId = chunk.getBuildId();
        entity.moduleName = null;
        entity.evidenceNo = chunk.getEvidenceId();
        entity.evidenceType = chunk.getEvidenceType() == null ? null : chunk.getEvidenceType().name();
        entity.sourceSystem = chunk.getSourceSystem() == null ? null : chunk.getSourceSystem().name();
        entity.docType = entity.evidenceType;
        entity.sourceType = entity.sourceSystem;
        entity.sourceUri = chunk.getSourceUrl();
        entity.sourceUrl = chunk.getSourceUrl();
        entity.filePath = chunk.getFilePath();
        entity.symbolName = chunk.getSymbolName();
        entity.lineStart = chunk.getLineStart();
        entity.lineEnd = chunk.getLineEnd();
        entity.lineRange = chunk.getLineRange();
        entity.chunkIndex = chunk.getChunkIndex();
        entity.contentHash = chunk.getContentHash();
        entity.title = chunk.getTitle();
        entity.keywords = chunk.getKeywords() == null ? "" : String.join(" ", chunk.getKeywords());
        entity.content = chunk.getContent();
        entity.metadata = JsonSupport.toJson(chunk.getMetadata() == null ? Map.of() : chunk.getMetadata());
        entity.vectorId = chunk.getVectorId();
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;
        return documentChunkRepository.save(entity);
    }

    public EvidenceRecordEntity getEvidence(String evidenceId) {
        return evidenceRecordRepository.findByEvidenceNo(evidenceId)
                .orElseThrow(() -> new BusinessException("EVIDENCE_NOT_FOUND", "Evidence not found: " + evidenceId));
    }

    public java.util.Optional<EvidenceRecordEntity> findEvidenceByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return java.util.Optional.empty();
        }
        return evidenceRecordRepository.findFirstByContentHashOrderByIdDesc(contentHash);
    }

    public java.util.Optional<EvidenceRecordEntity> findLatestLogicalEvidence(Evidence evidence) {
        if (evidence == null || evidence.getProjectKey() == null || evidence.getProjectKey().isBlank()) {
            return java.util.Optional.empty();
        }
        return evidenceRecordRepository.findLogicalEvidence(
                        evidence.getProjectKey(),
                        evidence.getBranch(),
                        evidence.getCommitId(),
                        evidence.getBuildId(),
                        evidence.getEvidenceType() == null ? null : evidence.getEvidenceType().name(),
                        evidence.getSourceSystem() == null ? null : evidence.getSourceSystem().name(),
                        evidence.getSourceUrl(),
                        evidence.getFilePath())
                .stream()
                .findFirst();
    }

    public DocumentChunkEntity getChunk(String chunkId) {
        return documentChunkRepository.findByChunkId(chunkId)
                .orElseThrow(() -> new BusinessException("CHUNK_NOT_FOUND", "Evidence chunk not found: " + chunkId));
    }

    public List<DocumentChunkEntity> findChunksByVectorIds(List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return List.of();
        }
        return documentChunkRepository.findByVectorIdIn(vectorIds);
    }

    public List<DocumentChunkEntity> findChunksByEvidenceNo(String evidenceNo) {
        if (evidenceNo == null || evidenceNo.isBlank()) {
            return List.of();
        }
        return documentChunkRepository.findByEvidenceNoOrderByChunkIndexAsc(evidenceNo);
    }

    public java.util.Optional<DocumentChunkEntity> findChunkByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return java.util.Optional.empty();
        }
        return documentChunkRepository.findFirstByContentHashOrderByIdDesc(contentHash);
    }

    public void deleteChunksByEvidenceNo(String evidenceNo) {
        if (evidenceNo != null && !evidenceNo.isBlank()) {
            documentChunkRepository.deleteByEvidenceNo(evidenceNo);
        }
    }

    public List<DocumentChunkEntity> findCandidateChunks(RagSearchRequest request) {
        Specification<DocumentChunkEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("projectKey"), request.projectKey()));
            addEquals(predicates, cb, root.get("branch"), request.branch());
            addEquals(predicates, cb, root.get("commitId"), request.commitId());
            addEquals(predicates, cb, root.get("buildId"), request.buildId());
            if (request.evidenceTypes() != null && !request.evidenceTypes().isEmpty()) {
                predicates.add(root.get("evidenceType").in(request.evidenceTypes().stream().map(EvidenceType::name).toList()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return findCandidateChunks(spec);
    }

    public List<DocumentChunkEntity> findCandidateChunks(Specification<DocumentChunkEntity> specification) {
        return documentChunkRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "id"));
    }

    public void saveRetrievalLog(RagSearchRequest request, int resultCount, long latencyMs) {
        RetrievalLogEntity entity = new RetrievalLogEntity();
        entity.setQueryText(request.query());
        entity.setProjectKey(request.projectKey());
        entity.setBranch(request.branch());
        entity.setCommitId(request.commitId());
        entity.setBuildId(request.buildId());
        entity.setEvidenceTypes(JsonSupport.toJson(request.evidenceTypes()));
        entity.setResultCount(resultCount);
        entity.setLatencyMs(latencyMs);
        entity.setCreatedAt(LocalDateTime.now());
        retrievalLogRepository.save(entity);
    }

    private void addEquals(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder cb,
                           jakarta.persistence.criteria.Path<String> path, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(cb.equal(path, value));
        }
    }
}
