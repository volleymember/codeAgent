package com.codeagent.rag.search;

import com.codeagent.rag.retrieval.HybridRetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HybridRetriever {
    private final HybridRetrievalService hybridRetrievalService;

    public HybridRetriever(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    public List<RagSearchResult> search(RagSearchRequest request) {
        return hybridRetrievalService.search(request);
    }
}
