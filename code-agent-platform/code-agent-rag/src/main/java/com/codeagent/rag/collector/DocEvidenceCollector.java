package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.SourceSystem;
import com.codeagent.storage.entity.DocumentEntity;
import com.codeagent.storage.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DocEvidenceCollector extends CollectorSupport implements EvidenceCollector {
    private final DocumentRepository documentRepository;

    public DocEvidenceCollector(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public SourceSystem sourceSystem() {
        return SourceSystem.DOC;
    }

    @Override
    public Evidence collect(IndexEvidenceRequest request) {
        if (request.getEvidenceType() != EvidenceType.DOCUMENT
                && request.getEvidenceType() != EvidenceType.MARKDOWN
                && request.getEvidenceType() != EvidenceType.JAVA_CODE) {
            throw new BusinessException("DOC_EVIDENCE_TYPE_UNSUPPORTED",
                    "Unsupported document evidence type: " + request.getEvidenceType());
        }
        DocumentEntity document = resolveDocument(request.getDocId());
        String content = fallback(request.getContent(), "");
        requireText(content, "DOCUMENT_CONTENT_REQUIRED",
                "content is required for document evidence indexing; original file metadata can be carried by rawRef/filePath.");
        Map<String, Object> metadata = metadata(request);
        put(metadata, "docId", request.getDocId());
        put(metadata, "rawRef", fallback(request.getRawRef(), document == null ? null : document.rawRef));
        log.info("Collected document evidence type={} projectKey={} docId={}",
                request.getEvidenceType(), request.getProjectKey(), request.getDocId());
        return Evidence.builder()
                .evidenceId(nextEvidenceId())
                .taskNo(request.getTaskNo())
                .projectKey(request.getProjectKey())
                .branch(request.getBranch())
                .commitId(request.getCommitId())
                .buildId(request.getBuildId())
                .evidenceType(request.getEvidenceType())
                .sourceSystem(SourceSystem.DOC)
                .sourceUrl(sourceUrl(request, document))
                .filePath(filePath(request, document))
                .symbolName(request.getSymbolName())
                .title(title(request, document))
                .summary(request.getSummary())
                .keywords(fallbackKeywords(request.getKeywords(), request.getEvidenceType(), request.getProjectKey()))
                .content(content)
                .rawRef(fallback(request.getRawRef(), document == null ? null : document.rawRef))
                .metadata(metadata)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private DocumentEntity resolveDocument(String docId) {
        if (docId == null || docId.isBlank()) {
            return null;
        }
        return documentRepository.findByDocId(docId).orElse(null);
    }

    private String sourceUrl(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getSourceUrl() != null && !request.getSourceUrl().isBlank()) {
            return request.getSourceUrl();
        }
        if (document != null && document.sourceUri != null && !document.sourceUri.isBlank()) {
            return document.sourceUri;
        }
        if (request.getRawRef() != null && !request.getRawRef().isBlank()) {
            return request.getRawRef();
        }
        return "doc://" + fallback(request.getDocId(), request.getProjectKey());
    }

    private String filePath(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getFilePath() != null && !request.getFilePath().isBlank()) {
            return request.getFilePath();
        }
        if (document != null && document.rawRef != null && !document.rawRef.isBlank()) {
            return document.rawRef;
        }
        return fallback(request.getDocId(), request.getProjectKey());
    }

    private String title(IndexEvidenceRequest request, DocumentEntity document) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle();
        }
        if (document != null && document.title != null && !document.title.isBlank()) {
            return document.title;
        }
        return "Document evidence";
    }

    private List<String> fallbackKeywords(List<String> keywords, EvidenceType evidenceType, String projectKey) {
        if (keywords != null && !keywords.isEmpty()) {
            return keywords;
        }
        return List.of("document", evidenceType.name().toLowerCase(), projectKey);
    }
}
