package com.codeagent.rag.rerank;

import com.codeagent.rag.search.RagSearchRequest;
import com.codeagent.rag.search.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class SymbolMatchScorer implements EvidenceScorer {
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^A-Za-z0-9_.$:#\\-]+");
    private static final Pattern EXCEPTION_OR_CODE = Pattern.compile("(?i).*(exception|error|fail|timeout|npe|\\b[A-Z][A-Z0-9_]{3,}\\b).*");

    @Override
    public double score(RagSearchRequest request, RagSearchResult result) {
        List<String> tokens = tokens(request.query());
        if (tokens.isEmpty()) {
            return 0.0;
        }
        String symbolName = lower(result.symbolName());
        String keywords = lower(result.keywords());
        String title = lower(result.title());
        String content = lower(result.content());
        double best = 0.0;
        int matched = 0;
        for (String token : tokens) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            if (!symbolName.isBlank() && symbolName.equals(lowerToken)) {
                best = Math.max(best, 1.0);
                matched++;
            } else if (!symbolName.isBlank() && symbolName.contains(lowerToken)) {
                best = Math.max(best, 0.86);
                matched++;
            } else if (!keywords.isBlank() && keywords.contains(lowerToken)) {
                best = Math.max(best, 0.74);
                matched++;
            } else if (!title.isBlank() && title.contains(lowerToken)) {
                best = Math.max(best, 0.62);
                matched++;
            } else if (EXCEPTION_OR_CODE.matcher(token).matches() && content.contains(lowerToken)) {
                best = Math.max(best, 0.68);
                matched++;
            } else if (content.contains(lowerToken)) {
                best = Math.max(best, 0.42);
                matched++;
            }
        }
        double coverage = matched == 0 ? 0.0 : (double) matched / tokens.size();
        return clamp(best * 0.75 + coverage * 0.25);
    }

    private List<String> tokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(TOKEN_SPLIT.split(query))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .distinct()
                .limit(16)
                .toList();
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
