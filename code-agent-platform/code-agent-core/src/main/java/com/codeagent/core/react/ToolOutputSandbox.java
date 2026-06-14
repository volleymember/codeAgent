package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.security.SensitiveDataMasker;
import com.codeagent.mcp.model.ToolCallResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ToolOutputSandbox {
    private static final int MAX_CHARS = 6000;
    private static final List<Pattern> KEY_LINE_PATTERNS = List.of(
            Pattern.compile("(?i)exception|error|failed|failure|caused by|at\\s+[\\w.$]+\\("),
            Pattern.compile("(?i)commit|branch|trace|stage|quality gate|coverage|duplicat|vulnerab|bug")
    );
    private static final Pattern EXCEPTION = Pattern.compile("\\b([A-Z][A-Za-z0-9_$.]*(?:Exception|Error))\\b");
    private static final Pattern COMMIT = Pattern.compile("\\b([0-9a-f]{7,40})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRANCH = Pattern.compile("(?i)\\bbranch\\s*[:=]\\s*([A-Za-z0-9._/\\-]+)");
    private static final Pattern FILE_LINE = Pattern.compile("([A-Za-z0-9_./\\-]+\\.(?:java|kt|go|ts|tsx|js|jsx|py|yml|yaml|xml)):(\\d+)");

    public CompressedToolObservation compress(ToolCallResult result) {
        String raw = rawText(result);
        int rawSize = raw.length();
        Redaction redaction = redact(raw);
        List<String> lines = dedupeLines(redaction.text());
        List<String> selected = selectLines(lines);
        int dropped = Math.max(0, lines.size() - selected.size());
        String compressed = String.join("\n", selected);
        if (compressed.length() > MAX_CHARS) {
            compressed = compressed.substring(0, MAX_CHARS) + "\n...[TRUNCATED]";
        }
        Map<String, Object> facts = extractFacts(compressed, result);
        return new CompressedToolObservation(result.toolName(), sourceSystem(result), compressed, facts,
                sanitizedEvidence(result.evidence()), redaction.count(), dropped, rawSize, compressed.length());
    }

    private String rawText(ToolCallResult result) {
        StringBuilder builder = new StringBuilder();
        append(builder, "status", result.status());
        append(builder, "summary", result.summary());
        append(builder, "errorMessage", result.errorMessage());
        append(builder, "rawRef", result.rawRef());
        for (EvidenceItem item : result.evidence()) {
            append(builder, "evidenceTitle", item.title());
            append(builder, "evidenceSummary", item.summary());
            append(builder, "filePath", item.filePath());
            append(builder, "lineRange", item.lineRange());
            if (item.metadata() != null && !item.metadata().isEmpty()) {
                append(builder, "metadata", item.metadata().toString());
            }
        }
        return builder.toString();
    }

    private Redaction redact(String raw) {
        String masked = SensitiveDataMasker.mask(raw);
        int count = Math.max(0, masked.split("\\*\\*\\*MASKED\\*\\*\\*", -1).length - 1);
        return new Redaction(masked, count);
    }

    private List<String> dedupeLines(String text) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            String normalized = line.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank()) {
                seen.add(normalized);
            }
        }
        return new ArrayList<>(seen);
    }

    private List<String> selectLines(List<String> lines) {
        List<String> selected = new ArrayList<>();
        for (String line : lines) {
            if (selected.size() >= 80) {
                break;
            }
            if (selected.size() < 12 || important(line)) {
                selected.add(line);
            }
        }
        return selected;
    }

    private boolean important(String line) {
        return KEY_LINE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(line).find());
    }

    private Map<String, Object> extractFacts(String text, ToolCallResult result) {
        Map<String, Object> facts = new LinkedHashMap<>();
        first(EXCEPTION, text).ifPresent(value -> facts.put("exceptionName", value));
        first(COMMIT, text).ifPresent(value -> facts.put("commitSha", value));
        first(BRANCH, text).ifPresent(value -> facts.put("branch", value));
        Matcher file = FILE_LINE.matcher(text);
        if (file.find()) {
            facts.put("filePath", file.group(1));
            facts.put("lineNumber", Integer.parseInt(file.group(2)));
        }
        facts.put("toolName", result.toolName());
        facts.put("status", result.status());
        facts.put("evidenceCount", result.evidence().size());
        return facts;
    }

    private List<EvidenceItem> sanitizedEvidence(List<EvidenceItem> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        return evidence.stream()
                .map(item -> new EvidenceItem(
                        item.sourceType(),
                        item.sourceSystem(),
                        SensitiveDataMasker.mask(item.title()),
                        SensitiveDataMasker.mask(item.summary()),
                        item.score(),
                        SensitiveDataMasker.mask(item.sourceUri()),
                        SensitiveDataMasker.mask(item.sourceUrl()),
                        SensitiveDataMasker.mask(item.filePath()),
                        SensitiveDataMasker.mask(item.lineRange()),
                        SensitiveDataMasker.mask(item.rawRef()),
                        SensitiveDataMasker.mask(item.matchReason()),
                        sanitizedMetadata(item.metadata())
                ))
                .toList();
    }

    private Map<String, Object> sanitizedMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> sanitized.put(key, value == null ? null : SensitiveDataMasker.mask(String.valueOf(value))));
        return sanitized;
    }

    private java.util.Optional<String> first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private String sourceSystem(ToolCallResult result) {
        return result.evidence().stream().findFirst().map(EvidenceItem::sourceSystem).orElse("UNKNOWN");
    }

    private void append(StringBuilder builder, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            builder.append(key).append(": ").append(value).append('\n');
        }
    }

    private record Redaction(String text, int count) {
    }
}
