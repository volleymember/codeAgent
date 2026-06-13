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

/**
 * 数据沙箱服务。
 *
 * <p>该服务用于对工具返回的原始结果进行“沙箱化”处理，
 * 将可能很长、很杂、包含敏感信息的原始输出压缩为适合 Agent 消费的上下文摘要。</p>
 *
 * <p>主要职责包括：</p>
 * <ul>
 *     <li>识别工具输出的数据类型，例如测试日志、Git diff、Sonar 报告等</li>
 *     <li>从原始输出中抽取关键证据</li>
 *     <li>清洗 ANSI 控制符和多余空白</li>
 *     <li>脱敏敏感数据</li>
 *     <li>控制摘要和证据的 token 数量</li>
 *     <li>生成结构化 facts 和压缩后的 EvidenceItem</li>
 * </ul>
 */
@Service
public class DataSandboxService {

    /**
     * ANSI 颜色/控制字符匹配模式。
     *
     * <p>用于清理控制台日志中的颜色控制符，避免污染摘要和证据文本。</p>
     */
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");

    /**
     * 重要日志行匹配模式。
     *
     * <p>用于从测试日志、编译日志、异常堆栈中提取包含 error、failed、exception、
     * timeout、BUILD FAILURE 等关键词的关键行。</p>
     */
    private static final Pattern IMPORTANT_LINE = Pattern.compile(
            "(?i).*(error|failed|failure|exception|caused by|assert|timeout|compilation failure|BUILD FAILURE|fatal|panic).*");

    /**
     * 数据沙箱配置。
     *
     * <p>包含最大关键证据数量、最大摘要 token、字符/token 比例等参数。</p>
     */
    private final DataSandboxProperties properties;

    /**
     * 创建数据沙箱服务。
     *
     * @param properties 数据沙箱配置
     */
    public DataSandboxService(DataSandboxProperties properties) {
        this.properties = properties;
    }

    /**
     * 对工具执行结果进行沙箱化处理。
     *
     * <p>处理流程如下：</p>
     * <ol>
     *     <li>将原始 payload 转为 JSON 文本并估算 token 数</li>
     *     <li>推断原始数据类型</li>
     *     <li>根据数据类型抽取关键证据</li>
     *     <li>构造结构化 facts</li>
     *     <li>生成压缩后的 summary</li>
     *     <li>对 EvidenceItem 做脱敏、截断和 metadata 增强</li>
     *     <li>返回 SandboxedToolPayload</li>
     * </ol>
     *
     * @param definition 工具定义
     * @param payload    工具执行产生的原始 payload
     * @param rawRef     原始输出存储引用
     * @return 沙箱化后的工具 payload
     */
    public SandboxedToolPayload sandbox(ToolDefinition definition, ToolExecutionPayload payload, String rawRef) {
        Object raw = payload.rawPayload();

        // 将原始输出统一转换为 JSON 文本，用于 token 估算和后续类型判断
        String rawText = JsonSupport.toJson(raw);
        int rawTokens = TokenEstimator.estimate(rawText, properties.getCharsPerToken());

        JsonNode rawNode = JsonSupport.mapper().valueToTree(raw);

        // 根据工具名称和原始内容推断数据类型
        DataArtifactType type = inferType(definition, rawNode, rawText);

        // 针对不同类型使用不同的关键证据抽取策略
        List<String> keyEvidence = switch (type) {
            case TEST_LOG, COMPILE_OUTPUT, CALL_CHAIN -> extractImportantLines(extractPrimaryText(rawNode, rawText));
            case GIT_DIFF -> extractGitDiffFacts(rawNode);
            case TEST_REPORT -> extractTestReportFacts(rawNode);
            case SONAR_REPORT -> extractSonarFacts(rawNode);
            default -> extractJsonFacts(rawNode);
        };

        // 如果类型专用抽取没有得到结果，则退化为重要行抽取
        if (keyEvidence.isEmpty()) {
            keyEvidence = extractImportantLines(extractPrimaryText(rawNode, rawText));
        }

        Map<String, Object> facts = structuredFacts(type, keyEvidence, rawRef, rawTokens);
        String summary = buildSummary(payload.summary(), type, keyEvidence, facts);

        int contextTokens = TokenEstimator.estimate(summary, properties.getCharsPerToken());

        // 对工具证据进行沙箱增强，追加 facts、脱敏和 token 截断
        List<EvidenceItem> evidence = sandboxEvidence(payload.evidence(), summary, facts, rawRef);

        double compressionRatio = rawTokens == 0 ? 1.0 : (double) contextTokens / rawTokens;

        return new SandboxedToolPayload(type, summary, evidence, facts, rawTokens, contextTokens, compressionRatio);
    }

    /**
     * 推断工具原始输出的数据类型。
     *
     * <p>优先根据工具名称判断，例如 diff、test_report、console_log、sonarqube；
     * 再根据文本内容判断是否为异常调用链；最后根据 JSON 节点类型回退为 JSON 或 TEXT。</p>
     *
     * @param definition 工具定义
     * @param rawNode    原始输出 JSON 节点
     * @param rawText    原始输出文本
     * @return 推断出的数据类型
     */
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

    /**
     * 从 Git diff 输出中抽取关键事实。
     *
     * <p>每个变更文件会统计新增行数和删除行数，并额外抽取 hunk 行或重要错误相关行。</p>
     *
     * @param rawNode Git diff 原始 JSON
     * @return 关键事实列表
     */
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

    /**
     * 从测试报告中抽取失败用例和统计信息。
     *
     * @param rawNode 测试报告 JSON
     * @return 测试报告关键事实列表
     */
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

    /**
     * 从 SonarQube 报告中抽取质量门禁、问题和度量信息。
     *
     * @param rawNode SonarQube 报告 JSON
     * @return SonarQube 关键事实列表
     */
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

    /**
     * 从普通 JSON 中递归抽取字段事实。
     *
     * @param rawNode 原始 JSON 节点
     * @return JSON 字段事实列表
     */
    private List<String> extractJsonFacts(JsonNode rawNode) {
        List<String> facts = new ArrayList<>();
        collectJsonFacts("", rawNode, facts);
        return facts;
    }

    /**
     * 递归收集 JSON 字段路径和值。
     *
     * <p>对象会展开字段，数组会记录 size 并最多展开前 5 个元素，
     * 值节点会记录为 path=value。</p>
     *
     * @param path  当前 JSON 路径
     * @param node  当前 JSON 节点
     * @param facts 收集到的事实列表
     */
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

    /**
     * 从文本中抽取重要行。
     *
     * <p>优先抽取包含错误、失败、异常、超时等关键词的行。
     * 如果没有匹配到重要行，则取前几行作为兜底摘要。</p>
     *
     * @param text 原始文本
     * @return 重要行列表
     */
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

    /**
     * 构造结构化事实 Map。
     *
     * @param type        数据类型
     * @param keyEvidence 关键证据列表
     * @param rawRef      原始输出引用
     * @param rawTokens   原始输出 token 估算值
     * @return 结构化 facts
     */
    private Map<String, Object> structuredFacts(DataArtifactType type, List<String> keyEvidence, String rawRef, int rawTokens) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("artifactType", type.name());
        facts.put("rawRef", rawRef);
        facts.put("rawTokens", rawTokens);
        facts.put("keyEvidenceCount", keyEvidence.size());
        facts.put("keyEvidence", keyEvidence.stream().limit(properties.getMaxKeyEvidence()).toList());
        return facts;
    }

    /**
     * 构建沙箱摘要。
     *
     * <p>摘要会包含原始摘要、数据类型、原始 token 数、关键证据数量和关键证据列表。
     * 最终结果会按最大摘要 token 限制截断。</p>
     *
     * @param originalSummary 原始工具摘要
     * @param type            数据类型
     * @param keyEvidence     关键证据列表
     * @param facts           结构化事实
     * @return 压缩后的摘要文本
     */
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

    /**
     * 对 EvidenceItem 进行沙箱化增强。
     *
     * <p>处理内容包括：</p>
     * <ul>
     *     <li>在 metadata 中追加 sandbox facts</li>
     *     <li>将沙箱摘要合并到证据摘要中</li>
     *     <li>对摘要脱敏并按 token 限制截断</li>
     *     <li>当 EvidenceItem 没有 rawRef 时使用工具原始输出 rawRef</li>
     * </ul>
     *
     * @param evidence       原始证据列表
     * @param sandboxSummary 沙箱摘要
     * @param facts          沙箱结构化事实
     * @param rawRef         原始输出引用
     * @return 沙箱化后的证据列表
     */
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

    /**
     * 从原始 JSON 中抽取主要文本内容。
     *
     * <p>优先读取 consoleLog，其次读取 log；如果都不存在，则使用完整 rawText。</p>
     *
     * @param rawNode 原始 JSON 节点
     * @param rawText 原始 JSON 文本
     * @return 主要文本内容
     */
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

    /**
     * 从 JSON 节点中读取文本字段。
     *
     * @param node     JSON 节点
     * @param key      字段名
     * @param fallback 默认值
     * @return 字段值；为空时返回 fallback
     */
    private String text(JsonNode node, String key, String fallback) {
        String value = node.path(key).asText("");
        return value.isBlank() ? fallback : value;
    }

    /**
     * 清洗单行文本。
     *
     * <p>处理步骤包括：</p>
     * <ol>
     *     <li>移除 ANSI 控制符</li>
     *     <li>压缩连续空白字符</li>
     *     <li>按 token 限制截断</li>
     *     <li>进行敏感数据脱敏</li>
     * </ol>
     *
     * @param line 原始文本行
     * @return 清洗后的文本行
     */
    private String cleanLine(String line) {
        if (line == null) {
            return "";
        }

        String cleaned = ANSI.matcher(line).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return SensitiveDataMasker.mask(TokenEstimator.truncateByTokens(cleaned, 80, properties.getCharsPerToken()));
    }
}