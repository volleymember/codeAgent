package com.codeagent.rag.embedding;

import com.codeagent.rag.model.EvidenceChunk;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.StringJoiner;

@Component
public class EmbeddingTextBuilder {
    public String build(EvidenceChunk chunk) {
        StringJoiner joiner = new StringJoiner("\n");
        append(joiner, "projectKey", chunk.getProjectKey());
        append(joiner, "evidenceType", chunk.getEvidenceType() == null ? null : chunk.getEvidenceType().name());
        append(joiner, "filePath", chunk.getFilePath());
        append(joiner, "symbolName", chunk.getSymbolName());
        append(joiner, "summary", chunk.getSummary());
        append(joiner, "keywords", join(chunk.getKeywords()));
        append(joiner, "content", chunk.getContent());
        return joiner.toString();
    }

    private void append(StringJoiner joiner, String name, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(name + ": " + value);
        }
    }

    private String join(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(", ", values);
    }
}
