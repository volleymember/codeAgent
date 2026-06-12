package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import com.codeagent.rag.model.EvidenceType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Order(20)
@Component
public class MarkdownChunker extends ChunkSupport implements EvidenceChunker {
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final int MAX_FALLBACK_LINES = 100;

    @Override
    public boolean supports(Evidence evidence) {
        String filePath = evidence.getFilePath() == null ? "" : evidence.getFilePath().toLowerCase();
        return evidence.getEvidenceType() == EvidenceType.MARKDOWN
                || evidence.getEvidenceType() == EvidenceType.DOCUMENT
                || filePath.endsWith(".md")
                || filePath.endsWith(".mdx")
                || filePath.endsWith(".markdown");
    }

    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        String[] lines = lines(evidence.getContent());
        if (lines.length == 0) {
            return List.of();
        }
        List<RangeStart> starts = new ArrayList<>();
        starts.add(new RangeStart(1, evidence.getTitle()));
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = HEADING.matcher(lines[i]);
            if (matcher.matches()) {
                starts.add(new RangeStart(i + 1, matcher.group(2)));
            }
        }
        if (starts.size() <= 1) {
            return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_FALLBACK_LINES));
        }
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            RangeStart start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1).line() - 1 : lines.length;
            ranges.add(new Range(start.line(), end, start.title(), start.title()));
        }
        return buildChunks(evidence, ranges);
    }

    private record RangeStart(int line, String title) {
    }
}
