package com.codeagent.rag.chunk;

import com.codeagent.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SimpleChunker {
    private final RagProperties properties;

    public SimpleChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> chunk(String title, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        int size = Math.max(100, properties.getChunkSize());
        int overlap = Math.min(Math.max(0, properties.getChunkOverlap()), size - 1);
        int start = 0;
        int index = 0;
        while (start < content.length()) {
            int end = Math.min(content.length(), start + size);
            int lineStart = lineNumber(content, start);
            int lineEnd = lineNumber(content, Math.max(start, end - 1));
            chunks.add(new DocumentChunk(title, content.substring(start, end), lineStart, lineEnd, index++));
            if (end == content.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }

    private int lineNumber(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
