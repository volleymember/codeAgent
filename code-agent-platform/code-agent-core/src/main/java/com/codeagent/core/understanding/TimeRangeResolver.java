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

@Service
public class TimeRangeResolver {
    private static final Pattern RECENT_HOURS = Pattern.compile("(?i)(?:last|最近|近)\\s*(\\d+)\\s*(?:h|hour|hours|小时)");
    private static final Pattern RECENT_DAYS = Pattern.compile("(?i)(?:last|最近|近)\\s*(\\d+)\\s*(?:d|day|days|天|日)");

    private final AgentProperties properties;

    public TimeRangeResolver(AgentProperties properties) {
        this.properties = properties;
    }

    public ResolvedTimeRange resolve(CreateAgentTaskCommand command,
                                     QueryUnderstandingResult understanding,
                                     IntentLeafView intent) {
        Instant now = Instant.now();
        if (hasText(command.startTime()) && hasText(command.endTime())) {
            Instant start = parse(command.startTime(), "startTime");
            Instant end = parse(command.endTime(), "endTime");
            if (!start.isBefore(end)) {
                throw new BusinessException("TIME_RANGE_INVALID", "startTime must be before endTime.");
            }
            return new ResolvedTimeRange(start, end, hours(start, end), "EXPLICIT", List.of());
        }
        Integer expressionHours = expressionHours(understanding == null ? null : understanding.timeExpression());
        int defaultHours = expressionHours == null ? defaultHours(intent) : expressionHours;
        Instant start = now.minus(Duration.ofHours(Math.max(1, defaultHours)));
        return new ResolvedTimeRange(start, now, Math.max(1, defaultHours),
                expressionHours == null ? "INTENT_DEFAULT" : "QUERY_TIME_EXPRESSION", List.of());
    }

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

    private Instant parse(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException("TIME_RANGE_INVALID", "Invalid ISO instant for " + field + ": " + value);
        }
    }

    private int hours(Instant start, Instant end) {
        return (int) Math.max(1, Math.ceil(Duration.between(start, end).toMinutes() / 60.0));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
