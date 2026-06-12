package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Order
@Component
public class PlainTextChunker extends ChunkSupport implements EvidenceChunker {
    private static final int MAX_LINES = 80;

    @Override
    public boolean supports(Evidence evidence) {
        return true;
    }

    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_LINES));
    }
}
