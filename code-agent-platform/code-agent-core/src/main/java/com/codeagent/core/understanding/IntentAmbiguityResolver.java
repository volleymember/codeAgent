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

/**
 * 意图歧义解析器。
 *
 * <p>该服务负责在意图分类完成后，判断当前用户请求是否已经足够清晰，
 * 是否可以直接进入上下文解析、工具规划和证据采集流程。</p>
 *
 * <p>歧义判断本身主要基于规则完成，例如：</p>
 * <ul>
 *     <li>没有匹配到有效意图</li>
 *     <li>最高候选意图置信度过低</li>
 *     <li>前两个候选意图分数过近</li>
 *     <li>缺少项目或服务信息</li>
 * </ul>
 *
 * <p>当规则判断需要澄清时，本类会尝试调用 LLM 生成一句简洁中文追问。
 * 如果 LLM 调用失败，则回退到确定性的默认澄清问题。</p>
 */
@Service
public class IntentAmbiguityResolver {

    /**
     * LLM 客户端。
     *
     * <p>用于在需要澄清时生成更自然的中文追问。</p>
     */
    private final LlmClient llmClient;

    /**
     * 创建意图歧义解析器。
     *
     * @param llmClient LLM 客户端
     */
    public IntentAmbiguityResolver(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 判断当前请求是否需要用户进一步澄清。
     *
     * <p>该方法先通过规则引擎计算歧义原因。如果没有歧义原因，则直接返回无需澄清。
     * 如果存在歧义原因，则生成一条澄清问题并返回 needsClarification=true。</p>
     *
     * @param taskNo         当前任务编号，用于 LLM 调用追踪
     * @param sessionId      当前会话编号，用于 LLM 调用追踪
     * @param command        创建 Agent 任务的命令参数
     * @param understanding  查询理解结果
     * @param classification 意图分类结果
     * @return 歧义判断结果
     */
    public AmbiguityDecision resolve(String taskNo,
                                     String sessionId,
                                     CreateAgentTaskCommand command,
                                     QueryUnderstandingResult understanding,
                                     IntentClassificationResult classification) {
        return resolve(taskNo, sessionId, command, understanding, classification, null, null);
    }

    public AmbiguityDecision resolve(String taskNo,
                                     String sessionId,
                                     CreateAgentTaskCommand command,
                                     QueryUnderstandingResult understanding,
                                     IntentClassificationResult classification,
                                     ProjectContext projectContext,
                                     com.codeagent.core.intent.dto.IntentLeafView intentLeaf) {
        String reason = ruleReason(command, understanding, classification, projectContext, intentLeaf);

        if (reason.isBlank()) {
            return new AmbiguityDecision(false, "", "");
        }

        return new AmbiguityDecision(true, reason,
                clarificationQuestion(taskNo, sessionId, command, understanding, classification, reason));
    }

    /**
     * 基于规则判断歧义原因。
     *
     * <p>该方法不会调用 LLM，而是根据意图分类结果和任务上下文字段进行确定性判断。
     * 返回空字符串表示当前输入足够清晰，可以继续执行。</p>
     *
     * @param command        创建 Agent 任务的命令参数
     * @param understanding  查询理解结果
     * @param classification 意图分类结果
     * @return 歧义原因编码；如果无歧义则返回空字符串
     */
    private String ruleReason(CreateAgentTaskCommand command,
                              QueryUnderstandingResult understanding,
                              IntentClassificationResult classification,
                              ProjectContext projectContext,
                              com.codeagent.core.intent.dto.IntentLeafView intentLeaf) {
        // 没有选中任何有效意图时，需要用户补充任务类型或明确排查目标。
        if (classification.selectedIntentCode() == null || classification.selectedIntentCode().isBlank()) {
            return "NO_VALID_INTENT";
        }

        List<IntentCandidate> candidates = classification.topCandidates();

        // top1 表示最高候选意图置信度；top2 表示第二候选意图置信度。
        double top1 = candidates.isEmpty() ? classification.confidence() : candidates.getFirst().confidence();
        double top2 = candidates.size() < 2 ? 0.0 : candidates.get(1).confidence();

        // 最高置信度过低，说明模型无法可靠判断用户意图。
        if (top1 < 0.60) {
            return "LOW_CONFIDENCE";
        }

        // 前两个候选意图分数过于接近，说明可能存在多种合理理解。
        if (top1 - top2 < 0.15) {
            return "CLOSE_INTENT_CANDIDATES";
        }

        // 虽然有候选意图，但置信度或区分度仍不足以直接执行。
        if (top1 < 0.75 || top1 - top2 < 0.20) {
            return "INTENT_NOT_CLEAR_ENOUGH";
        }

        boolean hasProject = hasText(command.projectKey()) || (understanding != null && !understanding.projectHints().isEmpty())
                || (projectContext != null && hasText(projectContext.projectKey()));
        boolean hasService = hasText(command.serviceName()) || (understanding != null && !understanding.serviceHints().isEmpty())
                || (projectContext != null && hasText(projectContext.serviceName()));

        // 没有项目或服务范围时，后续工具查询无法确定 GitLab、Jenkins、SonarQube 或日志范围。
        if (!hasProject && !hasService) {
            return "MISSING_PROJECT_OR_SERVICE";
        }

        if (projectContext != null && !projectContext.bindingFound()) {
            return "PROJECT_TOOL_BINDING_NOT_FOUND";
        }

        List<String> missingRequiredConfig = missingRequiredConfig(projectContext, intentLeaf, classification.selectedIntentCode());
        if (!missingRequiredConfig.isEmpty()) {
            return "MISSING_REQUIRED_PLATFORM_CONFIG:" + missingRequiredConfig;
        }

        return "";
    }

    private List<String> missingRequiredConfig(ProjectContext projectContext,
                                               com.codeagent.core.intent.dto.IntentLeafView intentLeaf,
                                               String intentCode) {
        if (projectContext == null || projectContext.missingConfigFields().isEmpty()) {
            return List.of();
        }
        List<String> required = intentLeaf != null && !intentLeaf.requiredConfigFields().isEmpty()
                ? intentLeaf.requiredConfigFields()
                : defaultRequiredConfig(intentCode);
        if (required.isEmpty()) {
            return List.of();
        }
        return required.stream()
                .filter(projectContext.missingConfigFields()::contains)
                .toList();
    }

    private List<String> defaultRequiredConfig(String intentCode) {
        if ("CI_FAILURE_ANALYSIS".equalsIgnoreCase(intentCode)) {
            return List.of("jenkinsJobName");
        }
        if ("MR_IMPACT_ANALYSIS".equalsIgnoreCase(intentCode)) {
            return List.of("gitlabProjectId");
        }
        if ("QUALITY_RISK_ANALYSIS".equalsIgnoreCase(intentCode)) {
            return List.of("sonarqubeProjectKey");
        }
        if ("PROD_INCIDENT_ANALYSIS".equalsIgnoreCase(intentCode)) {
            return List.of("logIndex", "apmServiceName");
        }
        return List.of();
    }

    /**
     * 生成澄清问题。
     *
     * <p>优先调用 LLM 根据歧义原因、任务命令、查询理解结果和意图分类结果生成一句中文追问。
     * 如果 LLM 调用失败、返回非 JSON 或没有有效问题，则回退到内置问题模板。</p>
     *
     * @param taskNo         当前任务编号
     * @param sessionId      当前会话编号
     * @param command        创建 Agent 任务的命令参数
     * @param understanding  查询理解结果
     * @param classification 意图分类结果
     * @param reason         规则判断得到的歧义原因
     * @return 中文澄清问题
     */
    private String clarificationQuestion(String taskNo,
                                         String sessionId,
                                         CreateAgentTaskCommand command,
                                         QueryUnderstandingResult understanding,
                                         IntentClassificationResult classification,
                                         String reason) {
        try {
            // 要求 LLM 只输出严格 JSON，便于稳定解析 clarificationQuestion 字段。
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
            // 澄清问题的措辞可以回退到确定性文本；是否需要澄清本身已经由规则决定。
        }

        // 缺少项目或服务时，优先提示用户补充查询范围。
        if ("MISSING_PROJECT_OR_SERVICE".equals(reason)) {
            return "请提供项目名或服务名，否则无法确定 Jenkins、GitLab、SonarQube 或日志查询范围。";
        }
        if (reason.startsWith("MISSING_REQUIRED_PLATFORM_CONFIG")) {
            return "当前项目缺少该意图所需的核心平台配置，请先由管理员补充项目工具绑定后再排查。";
        }
        if ("PROJECT_TOOL_BINDING_NOT_FOUND".equals(reason)) {
            return "无法识别该项目或服务的研发工具配置，请确认项目名/服务名，或联系管理员维护项目工具绑定。";
        }

        // 通用歧义澄清问题。
        return "当前问题可能匹配多个排查意图，请确认你要排查的是 CI 失败、线上故障、MR 影响、质量风险还是代码缺陷定位。";
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return 非 null 且非空白字符串时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
