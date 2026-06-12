package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class ChunkSupport {
    protected List<EvidenceChunk> buildChunks(Evidence evidence, List<Range> ranges) {
        String[] lines = lines(evidence.getContent());
        List<EvidenceChunk> chunks = new ArrayList<>();
        int index = 0;
        for (Range range : ranges) {
            int start = Math.max(1, range.startLine());
            int end = Math.min(lines.length, range.endLine());
            if (start > end) {
                continue;
            }
            String content = joinLines(lines, start, end);
            if (content.isBlank()) {
                continue;
            }
            chunks.add(EvidenceChunk.builder()
                    .evidenceId(evidence.getEvidenceId())
                    .projectKey(evidence.getProjectKey())
                    .branch(evidence.getBranch())
                    .commitId(evidence.getCommitId())
                    .buildId(evidence.getBuildId())
                    .evidenceType(evidence.getEvidenceType())
                    .sourceSystem(evidence.getSourceSystem())
                    .sourceUrl(evidence.getSourceUrl())
                    .filePath(evidence.getFilePath())
                    .symbolName(range.symbolName() == null ? evidence.getSymbolName() : range.symbolName())
                    .title(range.title() == null ? evidence.getTitle() : range.title())
                    .summary(evidence.getSummary())
                    .keywords(evidence.getKeywords())
                    .content(content)
                    .lineStart(start)
                    .lineEnd(end)
                    .lineRange(start + "-" + end)
                    .chunkIndex(index++)
                    .metadata(evidence.getMetadata() == null ? Map.of() : evidence.getMetadata())
                    .build());
        }
        return chunks;
    }

    protected List<Range> fixedRanges(String content, int maxLines) {
        String[] lines = lines(content);
        List<Range> ranges = new ArrayList<>();
        int start = 1;
        while (start <= lines.length) {
            int end = Math.min(lines.length, start + maxLines - 1);
            ranges.add(new Range(start, end, null, null));
            start = end + 1;
        }
        return ranges;
    }

    protected String[] lines(String content) {
        if (content == null || content.isBlank()) {
            return new String[0];
        }
        return content.split("\\R", -1);
    }

    private String joinLines(String[] lines, int startLine, int endLine) {
        StringBuilder builder = new StringBuilder();
        for (int i = startLine - 1; i < endLine; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }

    protected record Range(int startLine, int endLine, String title, String symbolName) {
    }
}
