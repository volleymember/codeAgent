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

@Service
public class IntentClassifier {
    private final LlmClient llmClient;

    public IntentClassifier(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public IntentClassificationResult classify(String taskNo,
                                               String sessionId,
                                               String originalQuery,
                                               QueryUnderstandingResult understanding,
                                               List<IntentLeafView> leaves,
                                               List<String> recentTurns,
                                               String compressedSummary) {
        Map<String, IntentLeafView> leafByCode = (leaves == null ? List.<IntentLeafView>of() : leaves).stream()
                .collect(Collectors.toMap(IntentLeafView::nodeCode, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        if (leafByCode.isEmpty()) {
            return new IntentClassificationResult(null, "", 0.0, List.of(), List.of(),
                    true, "当前没有启用的意图叶子节点，请先配置意图树。", Map.of());
        }

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
        String userPrompt = JsonSupport.toJson(Map.of(
                "originalQuery", originalQuery,
                "queryUnderstanding", understanding,
                "intentLeaves", leafByCode.values(),
                "recentTurns", recentTurns == null ? List.of() : recentTurns,
                "compressedSummary", compressedSummary == null ? "" : compressedSummary
        ));
        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(taskNo, sessionId,
                ModelTaskType.INTENT_CLASSIFICATION, systemPrompt, userPrompt, 2200, 0.1)).content());

        List<IntentCandidate> candidates = candidates(node.path("topCandidates"), leafByCode.keySet());
        String selectedCode = text(node, "selectedIntentCode");
        if (!leafByCode.containsKey(selectedCode)) {
            selectedCode = candidates.stream().findFirst().map(IntentCandidate::nodeCode).orElse(null);
        }
        if (!leafByCode.containsKey(selectedCode)) {
            selectedCode = leafByCode.containsKey("UNKNOWN") ? "UNKNOWN" : null;
        }
        if (selectedCode == null) {
            return new IntentClassificationResult(null, "", 0.0, candidates, List.of(), true,
                    "当前问题无法匹配到已启用的意图节点，请补充任务类型或联系管理员配置 UNKNOWN 意图。",
                    extractedFacts(node));
        }

        IntentLeafView selected = leafByCode.get(selectedCode);
        double confidence = node.path("confidence").asDouble(
                candidates.stream()
                        .filter(candidate -> selectedCode.equals(candidate.nodeCode()))
                        .findFirst()
                        .map(IntentCandidate::confidence)
                        .orElse(0.0));
        if (candidates.stream().noneMatch(candidate -> selectedCode.equals(candidate.nodeCode()))) {
            candidates = new ArrayList<>(candidates);
            candidates.add(new IntentCandidate(selectedCode, confidence, list(node, "matchedSignals")));
            candidates = candidates.stream()
                    .sorted(Comparator.comparingDouble(IntentCandidate::confidence).reversed())
                    .toList();
        }
        return new IntentClassificationResult(
                selectedCode,
                hasText(text(node, "selectedIntentPath")) ? text(node, "selectedIntentPath") : selected.nodePath(),
                confidence,
                candidates,
                list(node, "matchedSignals"),
                node.path("ambiguity").asBoolean(false),
                text(node, "clarificationQuestion"),
                extractedFacts(node)
        );
    }

    private List<IntentCandidate> candidates(JsonNode array, Set<String> allowedCodes) {
        if (!array.isArray()) {
            return List.of();
        }
        List<IntentCandidate> candidates = new ArrayList<>();
        for (JsonNode item : array) {
            String nodeCode = item.path("nodeCode").asText("");
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractedFacts(JsonNode node) {
        JsonNode value = node.path("extractedFacts");
        if (!value.isObject()) {
            return Map.of();
        }
        return JsonSupport.mapper().convertValue(value, LinkedHashMap.class);
    }

    private List<String> list(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        return JsonSupport.mapper().convertValue(value,
                JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
