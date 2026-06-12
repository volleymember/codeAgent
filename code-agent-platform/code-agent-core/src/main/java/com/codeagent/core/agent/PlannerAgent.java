package com.codeagent.core.agent;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolRouteCandidate;
import com.codeagent.mcp.model.ToolRouteRequest;
import com.codeagent.mcp.router.McpRouter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务规划 Agent。
 *
 * <p>负责根据任务类型、项目标识、用户提供的外部系统链接以及记忆中心上下文，
 * 选择合适的 MCP 工具，并生成后续执行阶段可使用的工具调用计划。</p>
 *
 * <p>该类本身不直接执行工具，只负责规划工具调用顺序和参数。
 * 实际工具选择由 {@link McpRouter} 完成。</p>
 */
@Component
public class PlannerAgent {
    private final McpRouter mcpRouter;

    /**
     * 创建任务规划 Agent。
     *
     * @param mcpRouter MCP 工具路由器，用于根据任务目标和输入条件选择候选工具
     */
    public PlannerAgent(McpRouter mcpRouter) {
        this.mcpRouter = mcpRouter;
    }

    /**
     * 根据任务命令生成工具调用计划。
     *
     * <p>该重载方法不使用记忆中心上下文，仅根据当前任务命令中的输入信息进行规划。</p>
     *
     * @param command 创建 Agent 任务的命令
     * @return 工具调用计划列表
     */
    public List<ToolPlan> plan(CreateAgentTaskCommand command) {
        return plan(command, null);
    }

    /**
     * 根据任务命令和记忆中心上下文生成工具调用计划。
     *
     * <p>该方法会先提取当前任务可用的输入信息，例如 GitLab MR、Jenkins 构建、
     * SonarQube 项目、Jira Issue、Confluence 页面和 OpenAPI 地址等；
     * 然后将任务目标、项目标识、可用输入以及记忆提示交给 {@link McpRouter}
     * 进行工具路由；最后把路由候选结果转换成有序的 {@link ToolPlan}。</p>
     *
     * @param command       创建 Agent 任务的命令
     * @param memoryContext 记忆中心上下文，可用于将历史经验作为规划提示；可为空
     * @return 工具调用计划列表
     */
    public List<ToolPlan> plan(CreateAgentTaskCommand command, MemoryCenterContext memoryContext) {
        return route(command, userGoal(command, memoryContext), "S", Set.of());
    }

    public List<ToolPlan> replan(CreateAgentTaskCommand command,
                                 MemoryCenterContext memoryContext,
                                 Map<String, Object> critique,
                                 List<ToolPlan> previousPlans,
                                 List<ToolCallResult> previousResults,
                                 List<EvidenceItem> evidence,
                                 int round) {
        Set<String> successfulTools = successfulTools(previousResults);
        String goal = userGoal(command, memoryContext)
                + " missingEvidence=" + value(critique == null ? null : critique.get("missingEvidence"))
                + " existingEvidence=" + evidenceHint(evidence)
                + " replanRound=" + round;
        List<ToolPlan> candidates = route(command, goal, "R" + round + "-", successfulTools);
        if (!candidates.isEmpty()) {
            return candidates;
        }
        Set<String> plannedTools = new HashSet<>();
        if (previousPlans != null) {
            previousPlans.stream().map(ToolPlan::toolName).forEach(plannedTools::add);
        }
        return route(command, goal + " retryFailedRequired=true", "R" + round + "F-", plannedTools);
    }

    private List<ToolPlan> route(CreateAgentTaskCommand command,
                                 String goal,
                                 String stepPrefix,
                                 Set<String> excludedTools) {
        // 提取当前任务可用的外部输入，路由器会根据这些输入判断哪些工具具备调用条件。
        Map<String, Object> inputs = availableInputs(command);

        // 构造工具路由请求。userGoal 中会包含任务类型、项目、可用输入以及可选的历史经验提示。
        List<ToolRouteCandidate> candidates = mcpRouter.routeTools(new ToolRouteRequest(
                command.taskType(),
                command.projectKey(),
                goal,
                inputs,
                30
        ));

        // 将路由器返回的候选工具转换为执行计划，并生成 S1、S2、S3... 形式的步骤编号。
        AtomicInteger index = new AtomicInteger(1);
        return candidates.stream()
                .filter(candidate -> excludedTools == null || !excludedTools.contains(candidate.toolName()))
                .map(candidate -> new ToolPlan(
                        stepPrefix + index.getAndIncrement(),
                        agentName(candidate.platform()),
                        candidate.toolName(),
                        candidate.input(),
                        candidate.required(),
                        candidate.score(),
                        candidate.reason(),
                        candidate.estimatedOutputTokens()
                ))
                .toList();
    }

    /**
     * 生成任务规划说明。
     *
     * <p>该重载方法不包含记忆中心信息，只描述任务目标、计划步骤、路由策略和停止条件。</p>
     *
     * @param command 创建 Agent 任务的命令
     * @param plans   已生成的工具调用计划
     * @return 可序列化的规划说明 Map
     */
    public Map<String, Object> describe(CreateAgentTaskCommand command, List<ToolPlan> plans) {
        return describe(command, plans, null);
    }

    /**
     * 生成任务规划说明。
     *
     * <p>该方法通常用于返回给前端、审计日志或任务详情页，帮助用户理解本次任务为什么会调用这些工具。
     * 如果传入记忆中心上下文，还会附带本次规划使用到的核心规则数量、召回经验数量和 Agent 备注数量。</p>
     *
     * @param command       创建 Agent 任务的命令
     * @param plans         已生成的工具调用计划
     * @param memoryContext 记忆中心上下文；可为空
     * @return 可序列化的规划说明 Map
     */
    public Map<String, Object> describe(CreateAgentTaskCommand command, List<ToolPlan> plans, MemoryCenterContext memoryContext) {
        Map<String, Object> payload = new LinkedHashMap<>();

        // 根据任务类型生成高层任务目标描述。
        payload.put("taskGoal", "CI_FAILURE_ANALYSIS".equalsIgnoreCase(command.taskType())
                ? "Analyze CI failure by collecting GitLab, Jenkins and SonarQube evidence."
                : "Analyze MR risk by collecting GitLab and SonarQube evidence.");

        // 放入本次规划出的工具执行步骤。
        payload.put("steps", plans);

        // 如果有记忆上下文，则补充本次规划可见的记忆统计信息。
        if (memoryContext != null) {
            payload.put("memoryCenter", Map.of(
                    "coreRules", memoryContext.coreRules().size(),
                    "recalledBugEpisodes", memoryContext.recalledEpisodes().size(),
                    "agentNotes", memoryContext.agentNotes().size()
            ));
        }

        // 描述路由器采用的策略，便于调试和审计。
        payload.put("router", Map.of(
                "strategy", "MCP_ROUTE_SCORE",
                "signal", "taskType + availableInputs + toolTags + estimatedOutputTokens",
                "costControl", "large-output tools are selected only when intent and required inputs justify them"
        ));

        // 描述执行阶段建议使用的停止条件。
        payload.put("stopCondition", Map.of("maxRounds", 3, "minConfidence", 0.75));
        return payload;
    }

    /**
     * 提取当前任务中可用于工具路由的输入参数。
     *
     * <p>只有非空输入会被放入 Map。这样路由器可以根据实际可用输入判断：
     * 哪些工具可以调用、哪些工具缺少必要参数、哪些工具应该降低优先级。</p>
     *
     * @param command 创建 Agent 任务的命令
     * @return 当前任务可用的输入参数 Map
     */
    private Map<String, Object> availableInputs(CreateAgentTaskCommand command) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        putIfPresent(inputs, "gitlabMrUrl", command.gitlabMrUrl());
        putIfPresent(inputs, "jenkinsBuildUrl", command.jenkinsBuildUrl());
        putIfPresent(inputs, "sonarqubeProjectKey", command.sonarqubeProjectKey());
        putIfPresent(inputs, "jiraIssueKey", command.jiraIssueKey());
        putIfPresent(inputs, "confluencePageUrl", command.confluencePageUrl());
        putIfPresent(inputs, "openApiUrl", command.openApiUrl());
        return inputs;
    }

    /**
     * 当字符串值非空时，将其写入输入 Map。
     *
     * @param inputs 输入参数 Map
     * @param key    参数名
     * @param value  参数值
     */
    private void putIfPresent(Map<String, Object> inputs, String key, String value) {
        if (hasText(value)) {
            inputs.put(key, value);
        }
    }

    /**
     * 构造用于工具路由的用户目标描述。
     *
     * <p>基础目标包含任务类型、项目标识以及 GitLab、Jenkins、SonarQube 等关键输入是否存在。
     * 当记忆中心上下文中存在召回经验时，会将最多 3 条历史经验的根因和修复内容拼接为
     * {@code memoryHints}，帮助路由器选择更符合当前问题特征的工具。</p>
     *
     * @param command       创建 Agent 任务的命令
     * @param memoryContext 记忆中心上下文；可为空
     * @return 路由器可使用的用户目标文本
     */
    private String userGoal(CreateAgentTaskCommand command, MemoryCenterContext memoryContext) {
        String base = "%s project=%s gitlab=%s jenkins=%s sonar=%s".formatted(
                command.taskType(),
                command.projectKey(),
                hasText(command.gitlabMrUrl()),
                hasText(command.jenkinsBuildUrl()),
                hasText(command.sonarqubeProjectKey()));

        if (memoryContext == null || memoryContext.recalledEpisodes().isEmpty()) {
            return base;
        }

        // 将历史经验中的根因和修复方案作为路由提示，最多使用前 3 条，避免目标文本过长。
        String memoryHints = memoryContext.recalledEpisodes().stream()
                .limit(3)
                .map(episode -> episode.rootCause() + " " + episode.fixContent())
                .reduce("", (left, right) -> left + " " + right);

        return base + " memoryHints=" + memoryHints;
    }

    private Set<String> successfulTools(List<ToolCallResult> results) {
        if (results == null) {
            return Set.of();
        }
        return results.stream()
                .filter(result -> "SUCCESS".equals(result.status()))
                .map(ToolCallResult::toolName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String evidenceHint(List<EvidenceItem> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "none";
        }
        return evidence.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(5)
                .map(item -> item.sourceSystem() + ":" + item.title() + ":" + item.matchReason())
                .reduce("", (left, right) -> left + " " + right)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 根据工具所属平台推断负责执行该工具的 Agent 名称。
     *
     * @param platform 工具平台名称，例如 Jenkins、GitLab、SonarQube
     * @return 对应的 Agent 名称
     */
    private String agentName(String platform) {
        if ("Jenkins".equalsIgnoreCase(platform)) {
            return "CIAnalysisAgent";
        }
        if ("GitLab".equalsIgnoreCase(platform)) {
            return "GitAnalysisAgent";
        }
        if ("SonarQube".equalsIgnoreCase(platform)) {
            return "QualityAgent";
        }
        return "ToolAgent";
    }

    /**
     * 判断字符串是否包含有效文本。
     *
     * @param value 待判断字符串
     * @return 当字符串非空且去除空白后仍有内容时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
