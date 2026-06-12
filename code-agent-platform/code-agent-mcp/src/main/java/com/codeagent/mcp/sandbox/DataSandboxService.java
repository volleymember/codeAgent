package com.codeagent.mcp.sandbox;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.security.SensitiveDataMasker;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.mcp.config.DataSandboxProperties;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.tool.ToolExecutionPayload;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DataSandboxService {
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern IMPORTANT_LINE = Pattern.compile(
            "(?i).*(error|failed|failure|exception|caused by|assert|timeout|compilation failure|BUILD FAILURE|fatal|panic).*");
    private final DataSandboxProperties properties;

    public DataSandboxService(DataSandboxProperties properties) {
        this.properties = properties;
    }

    public SandboxedToolPayload sandbox(ToolDefinition definition, ToolExecutionPayload payload, String rawRef) {
        Object raw = payload.rawPayload();
        String rawText = JsonSupport.toJson(raw);
        int rawTokens = TokenEstimator.estimate(rawText, properties.getCharsPerToken());
        JsonNode rawNode = JsonSupport.mapper().valueToTree(raw);
        DataArtifactType type = inferType(definition, rawNode, rawText);
        List<String> keyEvidence = switch (type) {
            case TEST_LOG, COMPILE_OUTPUT, CALL_CHAIN -> extractImportantLines(extractPrimaryText(rawNode, rawText));
            case GIT_DIFF -> extractGitDiffFacts(rawNode);
            case TEST_REPORT -> extractTestReportFacts(rawNode);
            case SONAR_REPORT -> extractSonarFacts(rawNode);
            default -> extractJsonFacts(rawNode);
        };
        if (keyEvidence.isEmpty()) {
            keyEvidence = extractImportantLines(extractPrimaryText(rawNode, rawText));
        }
        Map<String, Object> facts = structuredFacts(type, keyEvidence, rawRef, rawTokens);
        String summary = buildSummary(payload.summary(), type, keyEvidence, facts);
        int contextTokens = TokenEstimator.estimate(summary, properties.getCharsPerToken());
        List<EvidenceItem> evidence = sandboxEvidence(payload.evidence(), summary, facts, rawRef);
        double compressionRatio = rawTokens == 0 ? 1.0 : (double) contextTokens / rawTokens;
        return new SandboxedToolPayload(type, summary, evidence, facts, rawTokens, contextTokens, compressionRatio);
    }

    private DataArtifactType inferType(ToolDefinition definition, JsonNode rawNode, String rawText) {
        String name = definition.name().toLowerCase(Locale.ROOT);
        String text = rawText.toLowerCase(Locale.ROOT);
        if (name.contains("diff")) {
            return DataArtifactType.GIT_DIFF;
        }
        if (name.contains("test_report")) {
            return DataArtifactType.TEST_REPORT;
        }
        if (name.contains("console_log") || name.contains("log")) {
            return text.contains("compilation failure") || text.contains("build failure")
                    ? DataArtifactType.COMPILE_OUTPUT
                    : DataArtifactType.TEST_LOG;
        }
        if (name.startsWith("sonarqube.")) {
            return DataArtifactType.SONAR_REPORT;
        }
        if (text.contains("at ") && text.contains("exception")) {
            return DataArtifactType.CALL_CHAIN;
        }
        return rawNode.isObject() || rawNode.isArray() ? DataArtifactType.JSON : DataArtifactType.TEXT;
    }

    private List<String> extractGitDiffFacts(JsonNode rawNode) {
        List<String> facts = new ArrayList<>();
        JsonNode changes = rawNode.path("changes");
        if (!changes.isArray()) {
            return extractJsonFacts(rawNode);
        }
        int index = 0;
        for (JsonNode change : changes) {
            if (facts.size() >= properties.getMaxKeyEvidence()) {
                break;
            }
            String path = text(change, "new_path", text(change, "old_path", "unknown"));
            String diff = change.path("diff").asText("");
            long added = diff.lines().filter(line -> line.startsWith("+") && !line.startsWith("+++")).count();
            long removed = diff.lines().filter(line -> line.startsWith("-") && !line.startsWith("---")).count();
            facts.add("changedFile[%d]=%s added=%d removed=%d".formatted(++index, path, added, removed));
            diff.lines()
                    .filter(line -> line.startsWith("@@") || IMPORTANT_LINE.matcher(line).matches())
                    .limit(3)
                    .map(this::cleanLine)
                    .forEach(line -> facts.add("diffEvidence %s: %s".formatted(path, line)));
        }
        return facts;
    }

    private List<String> extractTestReportFacts(JsonNode rawNode) {
        List<String> facts = new ArrayList<>();
        facts.add("testTotals total=%d failed=%d skipped=%d".formatted(
                rawNode.path("totalCount").asInt(0),
                rawNode.path("failCount").asInt(0),
                rawNode.path("skipCount").asInt(0)));
        for (JsonNode suite : rawNode.path("suites")) {
            for (JsonNode kase : suite.path("cases")) {
                if (facts.size() >= properties.getMaxKeyEvidence()) {
                    return facts;
                }
                String status = kase.path("status").asText("");
                String error = kase.path("errorDetails").asText("");
                if ("FAILED".equalsIgnoreCase(status) || !error.isBlank()) {
                    facts.add("failedTest %s#%s status=%s error=%s".formatted(
                            suite.path("name").asText("unknown"),
                            kase.path("name").asText("unknown"),
                            status,
                            cleanLine(error)));
                }
            }
        }
        return facts;
    }

    private List<String> extractSonarFacts(JsonNode rawNode) {
        List<String> facts = new ArrayList<>();
        String gate = rawNode.path("projectStatus").path("status").asText("");
        if (!gate.isBlank()) {
            facts.add("qualityGate status=" + gate);
        }
        int total = rawNode.path("total").asInt(-1);
        if (total >= 0) {
            facts.add("issueTotal=" + total);
        }
        for (JsonNode issue : rawNode.path("issues")) {
            if (facts.size() >= properties.getMaxKeyEvidence()) {
                break;
            }
            facts.add("issue severity=%s type=%s component=%s line=%s message=%s".formatted(
                    issue.path("severity").asText("unknown"),
                    issue.path("type").asText("unknown"),
                    issue.path("component").asText("unknown"),
                    issue.path("line").asText("N/A"),
                    cleanLine(issue.path("message").asText(""))));
        }
        for (JsonNode measure : rawNode.path("component").path("measures")) {
            if (facts.size() >= properties.getMaxKeyEvidence()) {
                break;
            }
            facts.add("measure %s=%s".formatted(measure.path("metric").asText("unknown"),
                    measure.path("value").asText("unknown")));
        }
        return facts.isEmpty() ? extractJsonFacts(rawNode) : facts;
    }

    private List<String> extractJsonFacts(JsonNode rawNode) {
        List<String> facts = new ArrayList<>();
        collectJsonFacts("", rawNode, facts);
        return facts;
    }

    private void collectJsonFacts(String path, JsonNode node, List<String> facts) {
        if (facts.size() >= properties.getMaxJsonFields() || node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String value = cleanLine(node.asText());
            if (!value.isBlank()) {
                facts.add((path.isBlank() ? "value" : path) + "=" + value);
            }
            return;
        }
        if (node.isArray()) {
            facts.add((path.isBlank() ? "array" : path) + ".size=" + node.size());
            for (int i = 0; i < Math.min(5, node.size()); i++) {
                collectJsonFacts(path + "[" + i + "]", node.get(i), facts);
            }
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext() && facts.size() < properties.getMaxJsonFields()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String nextPath = path.isBlank() ? field.getKey() : path + "." + field.getKey();
            collectJsonFacts(nextPath, field.getValue(), facts);
        }
    }

    private List<String> extractImportantLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        text.lines()
                .map(this::cleanLine)
                .filter(line -> !line.isBlank())
                .filter(line -> IMPORTANT_LINE.matcher(line).matches())
                .limit(properties.getMaxKeyEvidence())
                .forEach(lines::add);
        if (lines.isEmpty()) {
            text.lines()
                    .map(this::cleanLine)
                    .filter(line -> !line.isBlank())
                    .limit(Math.min(8, properties.getMaxKeyEvidence()))
                    .forEach(lines::add);
        }
        return lines;
    }

    private Map<String, Object> structuredFacts(DataArtifactType type, List<String> keyEvidence, String rawRef, int rawTokens) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("artifactType", type.name());
        facts.put("rawRef", rawRef);
        facts.put("rawTokens", rawTokens);
        facts.put("keyEvidenceCount", keyEvidence.size());
        facts.put("keyEvidence", keyEvidence.stream().limit(properties.getMaxKeyEvidence()).toList());
        return facts;
    }

    private String buildSummary(String originalSummary,
                                DataArtifactType type,
                                List<String> keyEvidence,
                                Map<String, Object> facts) {
        StringBuilder summary = new StringBuilder();
        if (originalSummary != null && !originalSummary.isBlank()) {
            summary.append(SensitiveDataMasker.mask(originalSummary.trim())).append("\n");
        }
        summary.append("[DataSandbox] artifactType=").append(type.name())
                .append(", rawTokens=").append(facts.get("rawTokens"))
                .append(", keyEvidenceCount=").append(keyEvidence.size());
        int index = 1;
        for (String evidence : keyEvidence.stream().limit(properties.getMaxKeyEvidence()).toList()) {
            summary.append("\n- E").append(index++).append(": ").append(evidence);
        }
        return TokenEstimator.truncateByTokens(summary.toString(), properties.getMaxSummaryTokens(), properties.getCharsPerToken());
    }

    private List<EvidenceItem> sandboxEvidence(List<EvidenceItem> evidence,
                                               String sandboxSummary,
                                               Map<String, Object> facts,
                                               String rawRef) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        return evidence.stream()
                .map(item -> {
                    Map<String, Object> metadata = new LinkedHashMap<>(item.metadata());
                    metadata.put("sandbox", facts);
                    String summary = item.summary() == null || item.summary().isBlank()
                            ? sandboxSummary
                            : item.summary() + "\n" + sandboxSummary;
                    return new EvidenceItem(
                            item.sourceType(),
                            item.sourceSystem(),
                            item.title(),
                            TokenEstimator.truncateByTokens(SensitiveDataMasker.mask(summary),
                                    properties.getMaxEvidenceTokens(), properties.getCharsPerToken()),
                            item.score(),
                            item.sourceUri(),
                            item.sourceUrl(),
                            item.filePath(),
                            item.lineRange(),
                            item.rawRef() == null ? rawRef : item.rawRef(),
                            item.matchReason(),
                            metadata
                    );
                })
                .toList();
    }

    private String extractPrimaryText(JsonNode rawNode, String rawText) {
        JsonNode consoleLog = rawNode.path("consoleLog");
        if (!consoleLog.isMissingNode() && consoleLog.isTextual()) {
            return consoleLog.asText();
        }
        JsonNode log = rawNode.path("log");
        if (!log.isMissingNode() && log.isTextual()) {
            return log.asText();
        }
        return rawText;
    }

    private String text(JsonNode node, String key, String fallback) {
        String value = node.path(key).asText("");
        return value.isBlank() ? fallback : value;
    }

    private String cleanLine(String line) {
        if (line == null) {
            return "";
        }
        String cleaned = ANSI.matcher(line).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return SensitiveDataMasker.mask(TokenEstimator.truncateByTokens(cleaned, 80, properties.getCharsPerToken()));
    }
}
