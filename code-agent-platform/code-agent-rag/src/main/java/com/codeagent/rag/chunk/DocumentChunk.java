package com.codeagent.rag.chunk;

public record DocumentChunk(
        String title,
        String content,
        int lineStart,
        int lineEnd,
        int index
) {
}
