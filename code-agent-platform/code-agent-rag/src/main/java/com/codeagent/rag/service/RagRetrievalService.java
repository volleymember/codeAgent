package com.codeagent.rag.service;

import com.codeagent.common.domain.EvidencePackage;
import com.codeagent.rag.retrieval.HybridRetrievalService;
import com.codeagent.rag.search.RagSearchRequest;
import org.springframework.stereotype.Service;

@Service
public class RagRetrievalService {
    private final HybridRetrievalService hybridRetrievalService;
    private final EvidencePackageBuilder evidencePackageBuilder;

    public RagRetrievalService(HybridRetrievalService hybridRetrievalService, EvidencePackageBuilder evidencePackageBuilder) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.evidencePackageBuilder = evidencePackageBuilder;
    }

    public EvidencePackage search(RagSearchRequest request) {
        return evidencePackageBuilder.build(request, hybridRetrievalService.search(request));
    }
}
