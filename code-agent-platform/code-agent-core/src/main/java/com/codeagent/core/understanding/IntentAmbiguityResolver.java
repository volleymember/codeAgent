package com.codeagent.core.understanding;

import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IntentAmbiguityResolver {
    private final LlmClient llmClient;

    public IntentAmbiguityResolver(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public AmbiguityDecision resolve(String taskNo,
                                     String sessionId,
                                     CreateAgentTaskCommand command,
                                     QueryUnderstandingResult understanding,
                                     IntentClassificationResult classification) {
        String reason = ruleReason(command, understanding, classification);
        if (reason.isBlank()) {
            return new AmbiguityDecision(false, "", "");
        }
        return new AmbiguityDecision(true, reason,
                clarificationQuestion(taskNo, sessionId, command, understanding, classification, reason));
    }

    private String ruleReason(CreateAgentTaskCommand command,
                              QueryUnderstandingResult understanding,
                              IntentClassificationResult classification) {
        if (classification.selectedIntentCode() == null || classification.selectedIntentCode().isBlank()) {
            return "NO_VALID_INTENT";
        }
        List<IntentCandidate> candidates = classification.topCandidates();
        double top1 = candidates.isEmpty() ? classification.confidence() : candidates.getFirst().confidence();
        double top2 = candidates.size() < 2 ? 0.0 : candidates.get(1).confidence();
        if (top1 < 0.60) {
            return "LOW_CONFIDENCE";
        }
        if (top1 - top2 < 0.15) {
            return "CLOSE_INTENT_CANDIDATES";
        }
        if (top1 < 0.75 || top1 - top2 < 0.20) {
            return "INTENT_NOT_CLEAR_ENOUGH";
        }
        boolean hasProject = hasText(command.projectKey()) || !understanding.projectHints().isEmpty();
        boolean hasService = hasText(command.serviceName()) || !understanding.serviceHints().isEmpty();
        if (!hasProject && !hasService) {
            return "MISSING_PROJECT_OR_SERVICE";
        }
        return "";
    }

    private String clarificationQuestion(String taskNo,
                                         String sessionId,
                                         CreateAgentTaskCommand command,
                                         QueryUnderstandingResult understanding,
                                         IntentClassificationResult classification,
                                         String reason) {
        try {
            String systemPrompt = """
                    You write one concise Chinese clarification question for an engineering assistant.
                    The rule engine already decided clarification is required.
                    Output strict JSON only: {"clarificationQuestion": "..."}
                    """;
            String userPrompt = JsonSupport.toJson(Map.of(
                    "reason", reason,
                    "taskCommand", command,
                    "queryUnderstanding", understanding,
                    "intentClassification", classification
            ));
            JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(taskNo, sessionId,
                    ModelTaskType.INTENT_CLASSIFICATION, systemPrompt, userPrompt, 600, 0.2)).content());
            String question = node.path("clarificationQuestion").asText("");
            if (hasText(question)) {
                return question;
            }
        } catch (Exception ignored) {
            // Clarification wording can fall back to deterministic text; the decision itself is rule based.
        }
        if ("MISSING_PROJECT_OR_SERVICE".equals(reason)) {
            return "请提供项目名或服务名，否则无法确定 Jenkins、GitLab、SonarQube 或日志查询范围。";
        }
        return "当前问题可能匹配多个排查意图，请确认你要排查的是 CI 失败、线上故障、MR 影响、质量风险还是代码缺陷定位。";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
