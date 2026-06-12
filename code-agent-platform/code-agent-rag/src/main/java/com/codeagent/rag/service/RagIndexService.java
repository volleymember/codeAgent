package com.codeagent.rag.service;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.chunk.EvidenceChunker;
import com.codeagent.rag.collector.EvidenceCollectorRegistry;
import com.codeagent.rag.index.EvidenceVectorIndexer;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.IndexEvidenceResponse;
import com.codeagent.rag.store.EvidenceStore;
import com.codeagent.storage.entity.DocumentChunkEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import com.codeagent.storage.evidence.MinioEvidenceStore;
import com.codeagent.storage.evidence.StoredEvidenceObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RagIndexService {
    private final EvidenceCollectorRegistry collectorRegistry;
    private final List<EvidenceChunker> chunkers;
    private final EvidenceVectorIndexer evidenceVectorIndexer;
    private final EvidenceStore evidenceStore;
    private final ContentHashService contentHashService;
    private final MinioEvidenceStore minioEvidenceStore;

    public RagIndexService(EvidenceCollectorRegistry collectorRegistry,
                           List<EvidenceChunker> chunkers,
                           EvidenceVectorIndexer evidenceVectorIndexer,
                           EvidenceStore evidenceStore,
                           ContentHashService contentHashService,
                           MinioEvidenceStore minioEvidenceStore) {
        this.collectorRegistry = collectorRegistry;
        this.chunkers = chunkers;
        this.evidenceVectorIndexer = evidenceVectorIndexer;
        this.evidenceStore = evidenceStore;
        this.contentHashService = contentHashService;
        this.minioEvidenceStore = minioEvidenceStore;
    }

    @Transactional
    public IndexEvidenceResponse index(IndexEvidenceRequest request) {
        validate(request);
        Evidence evidence = collectorRegistry.get(request.getSourceSystem()).collect(request);
        if (evidence.getContent() == null || evidence.getContent().isBlank()) {
            throw new BusinessException("EVIDENCE_CONTENT_EMPTY", "Collected evidence content must not be empty.");
        }
        String contentHash = contentHashService.hashEvidence(evidence);
        evidence.setContentHash(contentHash);
        Optional<EvidenceRecordEntity> duplicated = evidenceStore.findEvidenceByContentHash(contentHash);
        if (duplicated.isPresent()) {
            EvidenceRecordEntity existing = duplicated.get();
            List<DocumentChunkEntity> existingChunks = evidenceStore.findChunksByEvidenceNo(existing.evidenceNo);
            log.info("Skip RAG evidence indexing by content hash evidenceId={} contentHash={}",
                    existing.evidenceNo, contentHash);
            return skippedResponse(existing, existingChunks);
        }
        Optional<EvidenceRecordEntity> previous = evidenceStore.findLatestLogicalEvidence(evidence);
        previous.ifPresent(existing -> {
            log.info("Detected RAG evidence content change projectKey={} previousEvidenceId={} oldHash={} newHash={}",
                    evidence.getProjectKey(), existing.evidenceNo, existing.contentHash, contentHash);
            evidence.setEvidenceId(existing.evidenceNo);
            deleteExistingChunksAndVectors(existing.evidenceNo);
        });
        StoredEvidenceObject storedObject = minioEvidenceStore.uploadOriginalContent(
                evidence.getProjectKey(),
                evidence.getEvidenceId(),
                evidence.getFilePath(),
                evidence.getContent(),
                contentHash);
        evidence.setObjectId(storedObject.objectId());
        evidence.setFileSize(storedObject.fileSize());
        evidenceStore.saveEvidence(evidence);
        evidenceStore.saveDocument(evidence);
        List<EvidenceChunk> chunks = selectChunker(evidence).chunk(evidence);
        if (chunks.isEmpty()) {
            throw new BusinessException("EVIDENCE_CHUNK_EMPTY", "No chunks generated for evidence: " + evidence.getEvidenceId());
        }
        List<String> chunkIds = new ArrayList<>();
        List<String> vectorIds = new ArrayList<>();
        for (EvidenceChunk chunk : chunks) {
            chunk.setChunkId(nextChunkId(evidence.getEvidenceId(), chunk.getChunkIndex()));
            chunk.setVectorId(nextVectorId());
            chunk.setContentHash(contentHashService.hashChunk(chunk));
            evidenceVectorIndexer.index(chunk);
            evidenceStore.saveChunk(chunk);
            chunkIds.add(chunk.getChunkId());
            vectorIds.add(chunk.getVectorId());
        }
        log.info("Indexed RAG evidence evidenceId={} chunks={} projectKey={}",
                evidence.getEvidenceId(), chunks.size(), evidence.getProjectKey());
        return IndexEvidenceResponse.builder()
                .evidenceId(evidence.getEvidenceId())
                .skipped(false)
                .contentHash(contentHash)
                .objectId(storedObject.objectId())
                .fileSize(storedObject.fileSize())
                .chunkCount(chunks.size())
                .chunkIds(chunkIds)
                .vectorIds(vectorIds)
                .build();
    }

    private IndexEvidenceResponse skippedResponse(EvidenceRecordEntity existing, List<DocumentChunkEntity> existingChunks) {
        List<String> chunkIds = existingChunks.stream().map(chunk -> chunk.chunkId).toList();
        List<String> vectorIds = existingChunks.stream()
                .map(chunk -> chunk.vectorId)
                .filter(vectorId -> vectorId != null && !vectorId.isBlank())
                .toList();
        return IndexEvidenceResponse.builder()
                .evidenceId(existing.evidenceNo)
                .skipped(true)
                .contentHash(existing.contentHash)
                .objectId(existing.objectId)
                .fileSize(existing.fileSize)
                .chunkCount(existingChunks.size())
                .chunkIds(chunkIds)
                .vectorIds(vectorIds)
                .build();
    }

    private void deleteExistingChunksAndVectors(String evidenceId) {
        List<DocumentChunkEntity> oldChunks = evidenceStore.findChunksByEvidenceNo(evidenceId);
        for (DocumentChunkEntity oldChunk : oldChunks) {
            if (oldChunk.vectorId != null && !oldChunk.vectorId.isBlank()) {
                evidenceVectorIndexer.delete(oldChunk.vectorId);
            }
        }
        evidenceStore.deleteChunksByEvidenceNo(evidenceId);
    }

    private EvidenceChunker selectChunker(Evidence evidence) {
        return chunkers.stream()
                .filter(chunker -> chunker.supports(evidence))
                .findFirst()
                .orElseThrow(() -> new BusinessException("CHUNKER_NOT_FOUND",
                        "No chunker for evidence: " + evidence.getEvidenceType()));
    }

    private void validate(IndexEvidenceRequest request) {
        if (request == null) {
            throw new BusinessException("INDEX_REQUEST_EMPTY", "Index evidence request must not be null.");
        }
        if (request.getProjectKey() == null || request.getProjectKey().isBlank()) {
            throw new BusinessException("PROJECT_KEY_REQUIRED", "projectKey must not be empty.");
        }
        if (request.getSourceSystem() == null) {
            throw new BusinessException("SOURCE_SYSTEM_REQUIRED", "sourceSystem must not be null.");
        }
        if (request.getEvidenceType() == null) {
            throw new BusinessException("EVIDENCE_TYPE_REQUIRED", "evidenceType must not be null.");
        }
    }

    private String nextChunkId(String evidenceId, int chunkIndex) {
        return "%s-C%04d".formatted(evidenceId, chunkIndex);
    }

    private String nextVectorId() {
        return "VEC-" + UUID.randomUUID();
    }

}
