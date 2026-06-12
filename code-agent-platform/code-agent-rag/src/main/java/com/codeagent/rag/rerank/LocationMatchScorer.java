package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LocationMatchScorer implements EvidenceScorer {
    private static final Pattern LINE_NUMBER = Pattern.compile("(?i)(?:line|行|:)?\\s*(\\d{1,6})");

    @Override
    public double score(RagSearchRequest request, RagSearchResult result) {
        Integer start = result.lineStart();
        Integer end = result.lineEnd();
        if ((start == null || end == null) && result.lineRange() != null) {
            int[] parsed = parseLineRange(result.lineRange());
            start = parsed[0];
            end = parsed[1];
        }
        if (start == null || end == null || start <= 0 || end < start) {
            return 0.25;
        }
        Integer queryLine = queryLine(request.query());
        if (queryLine != null) {
            if (queryLine >= start && queryLine <= end) {
                return 1.0;
            }
            int distance = Math.min(Math.abs(queryLine - start), Math.abs(queryLine - end));
            if (distance <= 5) {
                return 0.86;
            }
            if (distance <= 20) {
                return 0.62;
            }
            return 0.35;
        }
        int span = Math.max(1, end - start + 1);
        double compactness = span <= 20 ? 0.88 : span <= 80 ? 0.68 : span <= 160 ? 0.48 : 0.32;
        double position = start <= 80 ? 0.12 : 0.0;
        return clamp(compactness + position);
    }

    private Integer queryLine(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Matcher matcher = LINE_NUMBER.matcher(query);
        while (matcher.find()) {
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int[] parseLineRange(String lineRange) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)").matcher(lineRange);
        if (matcher.find()) {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        }
        return new int[]{0, 0};
    }
}
