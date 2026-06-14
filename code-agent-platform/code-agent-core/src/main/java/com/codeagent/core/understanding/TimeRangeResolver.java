package com.codeagent.core.understanding;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间范围解析器。
 *
 * <p>该服务负责为 Agent 调查任务解析需要查询的时间窗口。
 * 时间窗口通常用于日志查询、监控查询、构建记录查询等工具调用场景。</p>
 *
 * <p>解析优先级如下：</p>
 * <ol>
 *     <li>如果用户请求中显式提供 startTime 和 endTime，则使用显式时间范围</li>
 *     <li>否则尝试从查询理解结果中的 timeExpression 解析“最近 N 小时 / 最近 N 天”</li>
 *     <li>如果仍未解析到时间表达式，则根据意图节点配置或内置规则使用默认时间范围</li>
 * </ol>
 *
 * <p>该服务还提供高成本工具时间范围校验能力，用于限制日志、监控等高成本查询的默认扫描范围。</p>
 */
@Service
public class TimeRangeResolver {

    /**
     * “最近 N 小时”时间表达式匹配模式。
     *
     * <p>支持英文和中文表达，例如 last 3 hours、最近 2 小时、近 4h。</p>
     */
    private static final Pattern RECENT_HOURS = Pattern.compile("(?i)(?:last|最近|近)\\s*(\\d+)\\s*(?:h|hour|hours|小时)");

    /**
     * “最近 N 天”时间表达式匹配模式。
     *
     * <p>支持英文和中文表达，例如 last 2 days、最近 3 天、近 1d。</p>
     */
    private static final Pattern RECENT_DAYS = Pattern.compile("(?i)(?:last|最近|近)\\s*(\\d+)\\s*(?:d|day|days|天|日)");

    /**
     * Agent 配置。
     *
     * <p>用于读取 MCP ReAct 高成本工具时间范围限制等配置。</p>
     */
    private final AgentProperties properties;

    /**
     * 创建时间范围解析器。
     *
     * @param properties Agent 配置
     */
    public TimeRangeResolver(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析任务需要使用的时间范围。
     *
     * <p>如果 command 中同时提供 startTime 和 endTime，则按 ISO Instant 解析并校验 startTime 必须早于 endTime。
     * 如果没有显式时间范围，则尝试从 QueryUnderstandingResult.timeExpression 中解析相对时间。
     * 如果仍无法解析，则根据意图节点或内置规则使用默认小时数。</p>
     *
     * @param command       创建 Agent 任务的命令参数
     * @param understanding 查询理解结果，可为空
     * @param intent        当前选中的意图叶子节点，可为空
     * @return 解析后的时间范围
     * @throws BusinessException 当显式时间格式非法或 startTime 不早于 endTime 时抛出
     */
    public ResolvedTimeRange resolve(CreateAgentTaskCommand command,
                                     QueryUnderstandingResult understanding,
                                     IntentLeafView intent) {
        Instant now = Instant.now();

        // 优先使用用户显式传入的开始和结束时间。
        if (hasText(command.startTime()) && hasText(command.endTime())) {
            Instant start = parse(command.startTime(), "startTime");
            Instant end = parse(command.endTime(), "endTime");

            if (!start.isBefore(end)) {
                throw new BusinessException("TIME_RANGE_INVALID", "startTime must be before endTime.");
            }

            return new ResolvedTimeRange(start, end, hours(start, end), "EXPLICIT", List.of());
        }

        // 其次尝试从自然语言时间表达式中解析最近 N 小时 / 最近 N 天。
        Integer expressionHours = expressionHours(understanding == null ? null : understanding.timeExpression());

        // 如果没有解析到表达式，则使用意图默认时间范围。
        int defaultHours = expressionHours == null ? defaultHours(intent) : expressionHours;

        Instant start = now.minus(Duration.ofHours(Math.max(1, defaultHours)));

        return new ResolvedTimeRange(start, now, Math.max(1, defaultHours),
                expressionHours == null ? "INTENT_DEFAULT" : "QUERY_TIME_EXPRESSION", List.of());
    }

    /**
     * 校验高成本工具的时间范围。
     *
     * <p>某些工具，例如日志查询、监控查询，可能因为时间范围过大导致成本较高。
     * 当 highCost 为 true 且未允许高成本长时间范围查询时，
     * 如果 range.hours 超过配置上限，则抛出业务异常。</p>
     *
     * @param range    已解析的时间范围
     * @param highCost 当前工具是否属于高成本工具
     * @throws BusinessException 当高成本工具查询范围超过配置限制时抛出
     */
    public void enforceHighCostRange(ResolvedTimeRange range, boolean highCost) {
        if (!highCost || properties.getMcpReact().isAllowHighCostLongRange()) {
            return;
        }

        int maxHours = Math.max(1, properties.getMcpReact().getHighCostMaxTimeRangeHours());

        if (range != null && range.hours() > maxHours) {
            throw new BusinessException("HIGH_COST_TIME_RANGE_TOO_LARGE",
                    "High cost tools cannot query more than %d hours by default.".formatted(maxHours));
        }
    }

    /**
     * 获取意图默认时间范围。
     *
     * <p>优先使用意图叶子节点配置的 defaultTimeRangeHours。
     * 如果未配置，则根据常见意图编码使用内置默认值。</p>
     *
     * @param intent 当前意图叶子节点，可为空
     * @return 默认时间范围，单位小时
     */
    private int defaultHours(IntentLeafView intent) {
        if (intent != null && intent.defaultTimeRangeHours() != null && intent.defaultTimeRangeHours() > 0) {
            return intent.defaultTimeRangeHours();
        }

        String code = intent == null || intent.nodeCode() == null ? "" : intent.nodeCode().toUpperCase(Locale.ROOT);

        return switch (code) {
            case "PROD_INCIDENT_ANALYSIS" -> 6;
            case "MR_IMPACT_ANALYSIS", "QUALITY_RISK_ANALYSIS" -> 24 * 7;
            case "CODE_DEFECT_LOCALIZATION", "CI_FAILURE_ANALYSIS" -> 24;
            default -> 24;
        };
    }

    /**
     * 从自然语言时间表达式中解析小时数。
     *
     * <p>支持“最近 N 小时”和“最近 N 天”两类表达。
     * 天数会被转换为小时数。</p>
     *
     * @param expression 时间表达式
     * @return 解析出的小时数；无法解析时返回 null
     */
    private Integer expressionHours(String expression) {
        if (!hasText(expression)) {
            return null;
        }

        Matcher hourMatcher = RECENT_HOURS.matcher(expression);
        if (hourMatcher.find()) {
            return Math.max(1, Integer.parseInt(hourMatcher.group(1)));
        }

        Matcher dayMatcher = RECENT_DAYS.matcher(expression);
        if (dayMatcher.find()) {
            return Math.max(1, Integer.parseInt(dayMatcher.group(1)) * 24);
        }

        return null;
    }

    /**
     * 解析 ISO Instant 字符串。
     *
     * <p>示例格式：2026-06-14T08:00:00Z。</p>
     *
     * @param value 时间字符串
     * @param field 字段名，用于错误提示
     * @return 解析后的 Instant
     * @throws BusinessException 当时间字符串不是合法 ISO Instant 时抛出
     */
    private Instant parse(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException("TIME_RANGE_INVALID", "Invalid ISO instant for " + field + ": " + value);
        }
    }

    /**
     * 计算两个时间点之间的小时数。
     *
     * <p>不足 1 小时按 1 小时计算；存在分钟余数时向上取整。</p>
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 时间跨度小时数
     */
    private int hours(Instant start, Instant end) {
        return (int) Math.max(1, Math.ceil(Duration.between(start, end).toMinutes() / 60.0));
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