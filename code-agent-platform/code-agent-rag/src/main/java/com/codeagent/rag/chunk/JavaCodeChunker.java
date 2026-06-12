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

@Order(10)
@Component
public class JavaCodeChunker extends ChunkSupport implements EvidenceChunker {
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z\\d_$]*)\\b");
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "^\\s*(public|protected|private|static|final|native|synchronized|abstract|default|\\s)+[\\w<>\\[\\], ?]+\\s+([A-Za-z_$][A-Za-z\\d_$]*)\\s*\\([^;]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{\\s*$");
    private static final int MAX_FALLBACK_LINES = 120;

    @Override
    public boolean supports(Evidence evidence) {
        String filePath = evidence.getFilePath() == null ? "" : evidence.getFilePath().toLowerCase();
        return evidence.getEvidenceType() == EvidenceType.JAVA_CODE || filePath.endsWith(".java");
    }

    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        String[] lines = lines(evidence.getContent());
        if (lines.length == 0) {
            return List.of();
        }
        List<RangeStart> starts = new ArrayList<>();
        starts.add(new RangeStart(1, evidence.getTitle(), evidence.getSymbolName()));
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher typeMatcher = TYPE_DECLARATION.matcher(line);
            if (typeMatcher.find()) {
                starts.add(new RangeStart(i + 1, typeMatcher.group(2), typeMatcher.group(2)));
                continue;
            }
            Matcher methodMatcher = METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find() && !isControlStatement(methodMatcher.group(2))) {
                starts.add(new RangeStart(i + 1, methodMatcher.group(2), methodMatcher.group(2)));
            }
        }
        if (starts.size() <= 1) {
            return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_FALLBACK_LINES));
        }
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            RangeStart start = starts.get(i);
            int end = i + 1 < starts.size() ? starts.get(i + 1).line() - 1 : lines.length;
            ranges.add(new Range(start.line(), end, start.title(), start.symbolName()));
        }
        return buildChunks(evidence, ranges);
    }

    private boolean isControlStatement(String symbol) {
        return "if".equals(symbol) || "for".equals(symbol) || "while".equals(symbol)
                || "switch".equals(symbol) || "catch".equals(symbol);
    }

    private record RangeStart(int line, String title, String symbolName) {
    }
}
