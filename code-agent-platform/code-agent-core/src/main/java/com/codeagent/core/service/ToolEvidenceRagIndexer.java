package com.codeagent.core.service;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.rag.model.EvidenceType;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.SourceSystem;
import com.codeagent.rag.service.RagIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ToolEvidenceRagIndexer {
    private static final Logger log = LoggerFactory.getLogger(ToolEvidenceRagIndexer.class);

    private final ObjectProvider<RagIndexService> ragIndexService;

    public ToolEvidenceRagIndexer(ObjectProvider<RagIndexService> ragIndexService) {
        this.ragIndexService = ragIndexService;
    }

    public void index(String taskNo, String projectKey, List<EvidenceItem> evidence) {
        RagIndexService indexService = ragIndexService.getIfAvailable();
        if (indexService == null || evidence == null || evidence.isEmpty()) {
            return;
        }
        for (EvidenceItem item : evidence) {
            if (isRagDerived(item)) {
                continue;
            }
            try {
                indexService.index(request(taskNo, projectKey, item));
            } catch (Exception e) {
                log.warn("Tool evidence RAG indexing skipped taskNo={} sourceType={} title={} error={}",
                        taskNo, item.sourceType(), item.title(), e.getMessage());
            }
        }
    }

    private IndexEvidenceRequest request(String taskNo, String projectKey, EvidenceItem item) {
        IndexEvidenceRequest request = new IndexEvidenceRequest();
        request.setTaskNo(taskNo);
        request.setProjectKey(projectKey);
        request.setSourceSystem(SourceSystem.DOC);
        request.setEvidenceType(EvidenceType.DOCUMENT);
        request.setTitle(value(item.title(), "Tool evidence"));
        request.setSourceUrl(value(item.sourceUrl(), item.sourceUri()));
        request.setFilePath(value(item.filePath(), item.sourceType()));
        request.setRawRef(item.rawRef());
        request.setSummary(item.summary());
        request.setContent(content(item));
        request.setKeywords(keywords(item));
        request.setMetadata(Map.of(
                "originSourceSystem", value(item.sourceSystem(), "UNKNOWN"),
                "originSourceType", value(item.sourceType(), "UNKNOWN"),
                "originRawRef", value(item.rawRef(), "N/A"),
                "matchReason", value(item.matchReason(), "N/A"),
                "score", item.score()
        ));
        return request;
    }

    private boolean isRagDerived(EvidenceItem item) {
        return item.metadata().containsKey("chunkId") || item.metadata().containsKey("evidenceId");
    }

    private String content(EvidenceItem item) {
        return """
                title: %s
                sourceSystem: %s
                sourceType: %s
                sourceUrl: %s
                matchReason: %s
                summary:
                %s
                metadata:
                %s
                """.formatted(
                value(item.title(), "N/A"),
                value(item.sourceSystem(), "UNKNOWN"),
                value(item.sourceType(), "UNKNOWN"),
                value(item.sourceUrl(), item.sourceUri()),
                value(item.matchReason(), "N/A"),
                value(item.summary(), ""),
                JsonSupport.toJson(item.metadata())
        );
    }

    private List<String> keywords(EvidenceItem item) {
        List<String> values = new ArrayList<>();
        add(values, item.sourceSystem());
        add(values, item.sourceType());
        add(values, item.title());
        add(values, item.matchReason());
        return values.stream().distinct().limit(12).toList();
    }

    private void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
