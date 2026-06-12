package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.common.domain.EvidencePackage;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.IndexEvidenceResponse;
import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.service.RagIndexService;
import com.codeagent.rag.service.RagRetrievalService;
import com.codeagent.rag.store.EvidenceStore;
import com.codeagent.storage.entity.DocumentChunkEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final RagIndexService ragIndexService;
    private final RagRetrievalService ragRetrievalService;
    private final EvidenceStore evidenceStore;

    public RagController(RagIndexService ragIndexService,
                         RagRetrievalService ragRetrievalService,
                         EvidenceStore evidenceStore) {
        this.ragIndexService = ragIndexService;
        this.ragRetrievalService = ragRetrievalService;
        this.evidenceStore = evidenceStore;
    }

    @PostMapping("/index/evidence")
    public ApiResponse<IndexEvidenceResponse> indexEvidence(@Valid @RequestBody IndexEvidenceRequest request) {
        return ApiResponse.success(ragIndexService.index(request));
    }

    @PostMapping("/search")
    public ApiResponse<EvidencePackage> search(@Valid @RequestBody RagSearchRequest request) {
        return ApiResponse.success(ragRetrievalService.search(request));
    }

    @PostMapping("/hybrid-search")
    public ApiResponse<EvidencePackage> hybridSearch(@Valid @RequestBody RagSearchRequest request) {
        return ApiResponse.success(ragRetrievalService.search(request));
    }

    @GetMapping("/evidence/{evidenceId}")
    public ApiResponse<EvidenceRecordEntity> getEvidence(@PathVariable String evidenceId) {
        return ApiResponse.success(evidenceStore.getEvidence(evidenceId));
    }

    @GetMapping("/chunks/{chunkId}")
    public ApiResponse<DocumentChunkEntity> getChunk(@PathVariable String chunkId) {
        return ApiResponse.success(evidenceStore.getChunk(chunkId));
    }
}
