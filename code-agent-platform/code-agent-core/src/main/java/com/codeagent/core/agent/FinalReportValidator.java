package com.codeagent.core.agent;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.memory.model.CompressedMemoryContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class FinalReportValidator {
    public String render(String llmContent,
                         List<EvidenceItem> evidence,
                         Map<String, Object> critique,
                         CompressedMemoryContext memoryContext) {
        StructuredDiagnosis diagnosis = parse(llmContent)
                .map(parsed -> validate(parsed, evidence))
                .orElseGet(() -> fallback(evidence, critique));
        return toMarkdown(diagnosis, evidence, critique, memoryContext, parse(llmContent).isPresent());
    }

    private Optional<StructuredDiagnosis> parse(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String json = stripFence(content.trim());
        try {
            return Optional.of(JsonSupport.mapper().readValue(json, StructuredDiagnosis.class));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String stripFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstNewline = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstNewline < 0 || lastFence <= firstNewline) {
            return content;
        }
        return content.substring(firstNewline + 1, lastFence).trim();
    }

    private StructuredDiagnosis validate(StructuredDiagnosis diagnosis, List<EvidenceItem> evidence) {
        Set<String> validRefs = evidenceRefs(evidence);
        List<DiagnosisClaim> claims = new ArrayList<>();
        for (DiagnosisClaim claim : diagnosis.claims()) {
            List<String> refs = claim.evidenceRefs().stream()
                    .filter(validRefs::contains)
                    .distinct()
                    .toList();
            if (!refs.isEmpty()) {
                claims.add(new DiagnosisClaim(claim.claim(), refs, claim.confidence(), claim.counterEvidence()));
            }
        }
        if (claims.isEmpty()) {
            return fallback(evidence, Map.of("validator", "NO_VALID_EVIDENCE_REFS"));
        }
        return new StructuredDiagnosis(
                diagnosis.suspectedCause(),
                diagnosis.confidence(),
                claims,
                diagnosis.suggestedFix(),
                diagnosis.suggestedTests(),
                diagnosis.toolSummary(),
                diagnosis.uncertainties()
        );
    }

    private StructuredDiagnosis fallback(List<EvidenceItem> evidence, Map<String, Object> critique) {
        List<EvidenceItem> top = evidence == null ? List.of() : evidence.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(5)
                .toList();
        List<DiagnosisClaim> claims = top.stream()
                .map(item -> new DiagnosisClaim(
                        "%s: %s".formatted(value(item.title()), truncate(value(item.summary()), 80)),
                        List.of(ref(item)),
                        item.score(),
                        List.of()
                ))
                .toList();
        double confidence = critique == null || !(critique.get("confidence") instanceof Number number)
                ? top.stream().mapToDouble(EvidenceItem::score).average().orElse(0.0)
                : number.doubleValue();
        return new StructuredDiagnosis(
                top.isEmpty() ? "证据不足，无法确认根因。" : "基于最高分证据的候选根因，需结合当前证据逐条核验。",
                confidence,
                claims,
                "优先处理关键证据指向的失败点，并补充回归测试。",
                List.of("补充覆盖失败场景的单元测试或集成测试。"),
                top.stream().map(item -> "%s -> %s".formatted(value(item.sourceSystem()), value(item.title()))).toList(),
                top.isEmpty() ? List.of("当前 Evidence Pack 为空。") : List.of("LLM 未返回可校验的结构化诊断，已使用证据兜底报告。")
        );
    }

    private String toMarkdown(StructuredDiagnosis diagnosis,
                              List<EvidenceItem> evidence,
                              Map<String, Object> critique,
                              CompressedMemoryContext memoryContext,
                              boolean structuredOutputAccepted) {
        StringBuilder builder = new StringBuilder();
        builder.append("## 疑似原因\n\n")
                .append(diagnosis.suspectedCause()).append("\n\n")
                .append("## 关键证据\n\n");
        int index = 1;
        for (DiagnosisClaim claim : diagnosis.claims()) {
            builder.append(index++).append(". ")
                    .append(claim.claim())
                    .append(" [refs: ")
                    .append(String.join(", ", claim.evidenceRefs()))
                    .append(", confidence=")
                    .append("%.2f".formatted(claim.confidence()))
                    .append("]\n");
            if (!claim.counterEvidence().isEmpty()) {
                builder.append("   反证/冲突: ").append(String.join("; ", claim.counterEvidence())).append("\n");
            }
        }
        builder.append("\n## 证据来源链接\n\n");
        evidence.stream()
                .limit(12)
                .forEach(item -> builder.append("- ")
                        .append(ref(item)).append(": ")
                        .append(value(item.sourceUrl()))
                        .append(" / ")
                        .append(value(item.filePath()))
                        .append(" ")
                        .append(value(item.lineRange()))
                        .append("\n"));
        builder.append("\n## 置信度\n\n")
                .append("%.2f".formatted(diagnosis.confidence()))
                .append("\n\n## 建议修复方向\n\n")
                .append(diagnosis.suggestedFix())
                .append("\n\n## 建议补充测试\n\n");
        diagnosis.suggestedTests().forEach(test -> builder.append("- ").append(test).append("\n"));
        builder.append("\n## 已调用工具列表\n\n");
        diagnosis.toolSummary().forEach(tool -> builder.append("- ").append(tool).append("\n"));
        builder.append("\n## 无法确认的点\n\n");
        diagnosis.uncertainties().forEach(item -> builder.append("- ").append(item).append("\n"));
        builder.append("\n## 诊断校验\n\n")
                .append("- structuredOutputAccepted=").append(structuredOutputAccepted).append("\n")
                .append("- critique=").append(JsonSupport.toJson(critique == null ? Map.of() : critique)).append("\n")
                .append("- memoryPolicy=").append(memoryContext == null ? "EMPTY" : memoryContext.policy()).append("\n");
        return builder.toString();
    }

    private Set<String> evidenceRefs(List<EvidenceItem> evidence) {
        Set<String> refs = new LinkedHashSet<>();
        if (evidence != null) {
            evidence.forEach(item -> refs.add(ref(item)));
        }
        return refs;
    }

    private String ref(EvidenceItem item) {
        if (item == null) {
            return "N/A";
        }
        if (item.rawRef() != null && !item.rawRef().isBlank()) {
            return item.rawRef();
        }
        if (item.sourceUri() != null && !item.sourceUri().isBlank()) {
            return item.sourceUri();
        }
        return value(item.title());
    }

    private String truncate(String value, int tokens) {
        return TokenEstimator.truncateByTokens(value(value), tokens);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
