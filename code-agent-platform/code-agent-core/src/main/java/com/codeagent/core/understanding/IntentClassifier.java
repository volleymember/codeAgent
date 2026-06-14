package com.codeagent.core.understanding;

import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 意图分类器。
 *
 * <p>该服务负责根据用户原始查询、查询理解结果、当前启用的意图叶子节点、
 * 最近对话轮次和压缩上下文摘要，判断当前任务最匹配的意图节点。</p>
 *
 * <p>分类过程由 LLM 完成，但本类会对 LLM 输出进行严格约束和二次校验：
 * 只允许选择已启用的 IntentLeafView.nodeCode，禁止模型虚构意图编码。
 * 如果模型返回的 selectedIntentCode 不合法，则会尝试从 topCandidates 中选择候选项，
 * 或回退到 UNKNOWN 意图。</p>
 */
@Service
public class IntentClassifier {

    /**
     * LLM 客户端。
     *
     * <p>用于调用大模型完成意图分类。</p>
     */
    private final LlmClient llmClient;

    /**
     * 创建意图分类器。
     *
     * @param llmClient LLM 客户端
     */
    public IntentClassifier(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 对用户请求进行意图分类。
     *
     * <p>执行流程如下：</p>
     * <ol>
     *     <li>将启用的意图叶子节点按 nodeCode 建立索引</li>
     *     <li>如果没有可用叶子节点，则返回需要配置意图树的分类结果</li>
     *     <li>构造系统提示词，要求 LLM 只能从给定叶子节点中选择意图</li>
     *     <li>调用 LLM 并解析严格 JSON 输出</li>
     *     <li>过滤非法候选项，确保 selectedIntentCode 一定来自已启用节点</li>
     *     <li>补齐候选列表、置信度、匹配信号、歧义信息和抽取事实</li>
     * </ol>
     *
     * @param taskNo            当前任务编号，用于 LLM 调用追踪
     * @param sessionId         当前会话编号，用于 LLM 调用追踪
     * @param originalQuery     用户原始查询
     * @param understanding     查询理解结果
     * @param leaves            当前启用的意图叶子节点列表
     * @param recentTurns       最近对话轮次，可用于多轮上下文分类
     * @param compressedSummary 压缩后的历史上下文摘要
     * @return 意图分类结果
     */
    public IntentClassificationResult classify(String taskNo,
                                               String sessionId,
                                               String originalQuery,
                                               QueryUnderstandingResult understanding,
                                               List<IntentLeafView> leaves,
                                               List<String> recentTurns,
                                               String compressedSummary) {
        // 将叶子节点按照 nodeCode 建立索引，后续用于校验模型返回的 selectedIntentCode 是否合法。
        Map<String, IntentLeafView> leafByCode = (leaves == null ? List.<IntentLeafView>of() : leaves).stream()
                .collect(Collectors.toMap(IntentLeafView::nodeCode, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));

        // 没有启用的叶子节点时，无法进行意图分类，直接返回歧义结果。
        if (leafByCode.isEmpty()) {
            return new IntentClassificationResult(null, "", 0.0, List.of(), List.of(),
                    true, "当前没有启用的意图叶子节点，请先配置意图树。", Map.of());
        }

        // 系统提示词要求模型只能从 intentLeaves 中选择 nodeCode，并输出严格 JSON。
        String systemPrompt = """
                You are CodeAgent IntentClassifier.
                Choose the best matching leaf intent from the provided ACTIVE enabled leaves.
                You must only use nodeCode values from intentLeaves. Never invent a nodeCode.
                If uncertain, lower confidence and include close topCandidates.
                Output strict JSON only:
                {
                  "selectedIntentCode": "",
                  "selectedIntentPath": "",
                  "confidence": 0.0,
                  "topCandidates": [
                    {"nodeCode": "", "confidence": 0.0, "matchedSignals": []}
                  ],
                  "matchedSignals": [],
                  "ambiguity": false,
                  "clarificationQuestion": "",
                  "extractedFacts": {}
                }
                """;

        // 将用户查询、查询理解结果、候选意图、近期对话和压缩摘要一起提供给模型。
        String userPrompt = JsonSupport.toJson(Map.of(
                "originalQuery", originalQuery,
                "queryUnderstanding", understanding,
                "intentLeaves", leafByCode.values(),
                "recentTurns", recentTurns == null ? List.of() : recentTurns,
                "compressedSummary", compressedSummary == null ? "" : compressedSummary
        ));

        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(taskNo, sessionId,
                ModelTaskType.INTENT_CLASSIFICATION, systemPrompt, userPrompt, 2200, 0.1)).content());

        // 解析并过滤 topCandidates，只保留 nodeCode 存在于启用叶子节点中的候选项。
        List<IntentCandidate> candidates = candidates(node.path("topCandidates"), leafByCode.keySet());

        String selectedCode = text(node, "selectedIntentCode");

        // 如果模型返回的 selectedIntentCode 不合法，则尝试使用置信度最高的候选项。
        if (!leafByCode.containsKey(selectedCode)) {
            selectedCode = candidates.stream().findFirst().map(IntentCandidate::nodeCode).orElse(null);
        }

        // 如果候选项也无法使用，则尝试回退到 UNKNOWN 意图。
        if (!leafByCode.containsKey(selectedCode)) {
            selectedCode = leafByCode.containsKey("UNKNOWN") ? "UNKNOWN" : null;
        }

        // 如果仍然无法确定意图，则返回需要补充信息或配置 UNKNOWN 意图的结果。
        if (selectedCode == null) {
            return new IntentClassificationResult(null, "", 0.0, candidates, List.of(), true,
                    "当前问题无法匹配到已启用的意图节点，请补充任务类型或联系管理员配置 UNKNOWN 意图。",
                    extractedFacts(node));
        }

        String resolvedSelectedCode = selectedCode;
        IntentLeafView selected = leafByCode.get(resolvedSelectedCode);

        // 置信度优先使用模型输出；如果不存在，则从候选项中查找对应 nodeCode 的置信度。
        double confidence = node.path("confidence").asDouble(
                candidates.stream()
                        .filter(candidate -> resolvedSelectedCode.equals(candidate.nodeCode()))
                        .findFirst()
                        .map(IntentCandidate::confidence)
                        .orElse(0.0));

        // 如果 selectedCode 不在候选列表中，则主动补充一个候选项，保证结果自洽。
        if (candidates.stream().noneMatch(candidate -> resolvedSelectedCode.equals(candidate.nodeCode()))) {
            candidates = new ArrayList<>(candidates);
            candidates.add(new IntentCandidate(resolvedSelectedCode, confidence, list(node, "matchedSignals")));
            candidates = candidates.stream()
                    .sorted(Comparator.comparingDouble(IntentCandidate::confidence).reversed())
                    .toList();
        }

        return new IntentClassificationResult(
                resolvedSelectedCode,
                hasText(text(node, "selectedIntentPath")) ? text(node, "selectedIntentPath") : selected.nodePath(),
                confidence,
                candidates,
                list(node, "matchedSignals"),
                node.path("ambiguity").asBoolean(false),
                text(node, "clarificationQuestion"),
                extractedFacts(node)
        );
    }

    /**
     * 解析模型返回的 topCandidates。
     *
     * <p>该方法会过滤掉不在 allowedCodes 中的候选项，防止模型虚构 nodeCode。
     * 最终结果会按照 confidence 从高到低排序，并最多保留前 5 个候选项。</p>
     *
     * @param array        topCandidates JSON 数组
     * @param allowedCodes 当前启用意图叶子节点的 nodeCode 集合
     * @return 过滤和排序后的候选意图列表
     */
    private List<IntentCandidate> candidates(JsonNode array, Set<String> allowedCodes) {
        if (!array.isArray()) {
            return List.of();
        }

        List<IntentCandidate> candidates = new ArrayList<>();

        for (JsonNode item : array) {
            String nodeCode = item.path("nodeCode").asText("");

            // 只接受已启用意图叶子节点中的 nodeCode。
            if (!allowedCodes.contains(nodeCode)) {
                continue;
            }

            candidates.add(new IntentCandidate(nodeCode, item.path("confidence").asDouble(0.0),
                    list(item, "matchedSignals")));
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(IntentCandidate::confidence).reversed())
                .limit(5)
                .toList();
    }

    /**
     * 读取模型抽取出的结构化事实。
     *
     * <p>extractedFacts 用于承载模型在意图分类过程中识别到的补充事实，
     * 例如服务名、环境、错误类型、外部引用等。</p>
     *
     * @param node LLM 返回的 JSON 根节点
     * @return 结构化事实 Map；如果字段不存在或不是对象，则返回空 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractedFacts(JsonNode node) {
        JsonNode value = node.path("extractedFacts");

        if (!value.isObject()) {
            return Map.of();
        }

        return JsonSupport.mapper().convertValue(value, LinkedHashMap.class);
    }

    /**
     * 从 JSON 节点中读取字符串列表字段。
     *
     * <p>如果字段不存在或不是数组，则返回空列表。</p>
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 字符串列表
     */
    private List<String> list(JsonNode node, String field) {
        JsonNode value = node.path(field);

        if (!value.isArray()) {
            return List.of();
        }

        return JsonSupport.mapper().convertValue(value,
                JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
    }

    /**
     * 从 JSON 节点中读取文本字段。
     *
     * <p>返回值会进行 trim；如果字段不存在，则返回空字符串。</p>
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本字段值
     */
    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return 非 null 且非空白时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}