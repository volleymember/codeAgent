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
 * 查询理解服务。
 *
 * <p>该服务负责将用户提交的工程问题、排障请求或任务命令解析为结构化信息，
 * 例如关键词、症状、项目线索、服务线索、环境、时间表达式、错误信息、
 * traceId、commitSha 和 branch 等。</p>
 *
 * <p>解析过程通过 LLM 完成，但该类会对 LLM 返回结果进行 JSON 解析和字段兜底处理，
 * 确保后续意图分类、歧义判断、项目上下文解析和工具规划流程能够使用稳定的数据结构。</p>
 */
@Service
public class QueryUnderstandingService {

    /**
     * LLM 客户端。
     *
     * <p>用于调用大模型完成自然语言查询理解。</p>
     */
    private final LlmClient llmClient;

    /**
     * 创建查询理解服务。
     *
     * @param llmClient LLM 客户端
     */
    public QueryUnderstandingService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 理解用户查询并返回结构化结果。
     *
     * <p>该方法会先构造系统提示词，要求模型只输出严格 JSON；
     * 然后将原始查询和任务命令作为输入发送给 LLM；
     * 最后将模型返回的 JSON 解析为 {@link QueryUnderstandingResult}。</p>
     *
     * <p>如果部分字段缺失，会使用原始命令中的字段作为兜底，
     * 例如 commitSha 和 branch。</p>
     *
     * @param taskNo    当前任务编号，用于 LLM 调用追踪
     * @param sessionId 当前会话编号，用于 LLM 调用追踪
     * @param command   创建 Agent 任务的命令参数
     * @return 查询理解结果
     */
    public QueryUnderstandingResult understand(String taskNo, String sessionId, CreateAgentTaskCommand command) {
        // 系统提示词要求 LLM 输出严格 JSON，避免 markdown 或解释性文本影响解析。
        String systemPrompt = """
                You are CodeAgent QueryUnderstandingService.
                Parse the user's engineering investigation request into strict JSON only.
                Do not include markdown or explanatory text.
                Preserve uncertainty when fields are absent.
                Output exactly these fields:
                {
                  "originalQuery": "",
                  "normalizedQuery": "",
                  "keywords": [],
                  "symptoms": [],
                  "projectHints": [],
                  "serviceHints": [],
                  "environment": "",
                  "timeExpression": "",
                  "errorMessage": "",
                  "traceId": "",
                  "commitSha": "",
                  "branch": "",
                  "possibleExternalRefs": [],
                  "uncertainty": 0.0
                }
                """;

        // 生成原始查询文本：优先使用 command.query，否则从任务字段拼接一个可理解的查询。
        String original = originalQuery(command);

        // 将原始查询和完整任务命令一并传给 LLM，提升字段提取准确性。
        String userPrompt = JsonSupport.toJson(Map.of(
                "originalQuery", original,
                "taskCommand", command
        ));

        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(taskNo, sessionId,
                ModelTaskType.QUERY_UNDERSTANDING, systemPrompt, userPrompt, 1800, 0.1)).content());

        // 将 JSON 字段转换为强类型结果；缺失字段使用合理默认值或命令中的显式字段兜底。
        return new QueryUnderstandingResult(
                text(node, "originalQuery", original),
                text(node, "normalizedQuery", original),
                list(node, "keywords"),
                list(node, "symptoms"),
                list(node, "projectHints"),
                list(node, "serviceHints"),
                text(node, "environment", ""),
                text(node, "timeExpression", ""),
                text(node, "errorMessage", ""),
                text(node, "traceId", ""),
                text(node, "commitSha", command.commitSha()),
                text(node, "branch", command.branch()),
                list(node, "possibleExternalRefs"),
                node.path("uncertainty").asDouble(0.5)
        );
    }

    /**
     * 构造原始查询文本。
     *
     * <p>如果用户显式提供了 query，则直接使用 query；
     * 否则将任务类型、项目、服务、GitLab、Jenkins、SonarQube 和 Jira 等字段拼接成查询文本。
     * 这样即使用户没有填写自然语言描述，也能为 LLM 提供基础上下文。</p>
     *
     * @param command 创建 Agent 任务的命令参数
     * @return 原始查询文本
     */
    private String originalQuery(CreateAgentTaskCommand command) {
        if (hasText(command.query())) {
            return command.query();
        }

        return "%s project=%s service=%s gitlab=%s jenkins=%s sonar=%s jira=%s".formatted(
                        command.taskType(), command.projectKey(), value(command.serviceName()),
                        value(command.gitlabMrUrl()), value(command.jenkinsBuildUrl()),
                        value(command.sonarqubeProjectKey()), value(command.jiraIssueKey()))
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 从 JSON 节点中读取字符串列表字段。
     *
     * <p>如果目标字段不是数组，则返回空列表，避免调用方处理 null。</p>
     *
     * @param node  JSON 根节点
     * @param field 字段名
     * @return 字符串列表；字段不存在或不是数组时返回空列表
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
     * <p>如果字段为空或空白，则返回 fallback；
     * fallback 为 null 时会被转换为空字符串。</p>
     *
     * @param node     JSON 根节点
     * @param field    字段名
     * @param fallback 兜底文本
     * @return 字段文本或兜底文本
     */
    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return hasText(value) ? value : value(fallback);
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return 非 null 且不是空白字符串时返回 true
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 将 null 字符串转换为空字符串。
     *
     * @param value 原始字符串
     * @return 非 null 字符串
     */
    private String value(String value) {
        return value == null ? "" : value;
    }
}