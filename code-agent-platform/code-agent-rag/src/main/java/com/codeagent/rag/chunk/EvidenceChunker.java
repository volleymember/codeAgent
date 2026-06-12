package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;

import java.util.List;

public interface EvidenceChunker {
    boolean supports(Evidence evidence);

    List<EvidenceChunk> chunk(Evidence evidence);
}
