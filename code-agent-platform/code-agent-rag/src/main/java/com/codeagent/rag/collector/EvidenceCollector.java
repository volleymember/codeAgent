package com.codeagent.rag.collector;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.IndexEvidenceRequest;
import com.codeagent.rag.model.SourceSystem;

public interface EvidenceCollector {
    SourceSystem sourceSystem();

    Evidence collect(IndexEvidenceRequest request);
}
