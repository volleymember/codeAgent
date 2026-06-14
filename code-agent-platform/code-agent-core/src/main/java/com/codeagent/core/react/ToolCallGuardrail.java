package com.codeagent.core.react;

import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.understanding.ProjectContext;
import com.codeagent.core.understanding.ResolvedTimeRange;
import com.codeagent.core.understanding.TimeRangeResolver;
import com.codeagent.mcp.model.ToolDefinition;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolCallGuardrail {
    private final TimeRangeResolver timeRangeResolver;

    public ToolCallGuardrail(TimeRangeResolver timeRangeResolver) {
        this.timeRangeResolver = timeRangeResolver;
    }

    public ToolCallValidation validate(ToolPlanCandidate candidate,
                                       List<ToolDefinition> definitions,
                                       InvestigationContext context) {
        ToolDefinition definition = definitions.stream()
                .filter(item -> item.name().equals(candidate.toolName()))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            return reject(candidate, null, "TOOL_NOT_FOUND");
        }
        if (!allowedByIntent(definition, context.intentLeaf())) {
            return reject(candidate, definition, "TOOL_TYPE_NOT_ALLOWED_FOR_INTENT");
        }
        Map<String, Object> input = resolveInput(candidate.input(), definition, context);
        List<String> missing = definition.requiredInputs().stream()
                .filter(required -> !hasValue(input.get(required)))
                .toList();
        double completeness = definition.requiredInputs().isEmpty()
                ? 1.0
                : (definition.requiredInputs().size() - missing.size()) / (double) definition.requiredInputs().size();
        if (!missing.isEmpty()) {
            return new ToolCallValidation(false, candidate, definition, input,
                    "MISSING_REQUIRED_INPUTS:" + missing, completeness, intentScore(definition, context.intentLeaf()), false);
        }
        if (definition.highCost()) {
            try {
                timeRangeResolver.enforceHighCostRange(context.timeRange(), true);
            } catch (Exception e) {
                return new ToolCallValidation(false, candidate, definition, input,
                        "HIGH_COST_TIME_RANGE_TOO_LARGE", completeness, intentScore(definition, context.intentLeaf()), false);
            }
        }
        if (duplicateSuccessful(definition.name(), input, context.successfulToolCallKeys())) {
            return new ToolCallValidation(false, candidate, definition, input,
                    "DUPLICATE_SUCCESSFUL_TOOL_CALL", completeness, intentScore(definition, context.intentLeaf()), true);
        }
        if (!parametersLookTyped(input)) {
            return new ToolCallValidation(false, candidate, definition, input,
                    "PARAMETER_TYPE_INVALID", completeness, intentScore(definition, context.intentLeaf()), false);
        }
        return new ToolCallValidation(true, candidate, definition, input, "",
                completeness, intentScore(definition, context.intentLeaf()), false);
    }

    private Map<String, Object> resolveInput(Map<String, Object> proposed,
                                             ToolDefinition definition,
                                             InvestigationContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (proposed != null) {
            input.putAll(proposed);
        }
        CreateAgentTaskCommand command = context.command();
        putIfPresent(input, "gitlabMrUrl", command.gitlabMrUrl());
        putIfPresent(input, "jenkinsBuildUrl", command.jenkinsBuildUrl());
        putIfPresent(input, "sonarqubeProjectKey", command.sonarqubeProjectKey());
        putIfPresent(input, "jiraIssueKey", command.jiraIssueKey());
        putIfPresent(input, "confluencePageUrl", command.confluencePageUrl());
        putIfPresent(input, "openApiUrl", command.openApiUrl());
        if (context.projectContext() != null) {
            ProjectContext project = context.projectContext();
            putIfPresent(input, "sonarqubeProjectKey", project.sonarqubeProjectKey());
            putIfPresent(input, "gitlabProjectId", project.gitlabProjectId());
            putIfPresent(input, "gitlabRepoUrl", project.gitlabRepoUrl());
            putIfPresent(input, "jenkinsJobName", project.jenkinsJobName());
            putIfPresent(input, "jenkinsPipelineName", project.jenkinsPipelineName());
            putIfPresent(input, "repoName", project.repoName());
            putIfPresent(input, "serviceName", project.serviceName());
            putIfPresent(input, "apmServiceName", project.apmServiceName());
            putIfPresent(input, "logIndex", project.logIndex());
            putIfPresent(input, "alertGroup", project.alertGroup());
            putIfPresent(input, "ownerTeam", project.ownerTeam());
            putIfPresent(input, "branch", project.defaultBranch());
        }
        if (context.knownFacts() != null) {
            context.knownFacts().forEach((key, value) -> {
                if (hasValue(value)) {
                    input.putIfAbsent(key, value);
                }
            });
        }
        ResolvedTimeRange range = context.timeRange();
        if (range != null && (definition.requiredInputs().contains("timeRange")
                || definition.optionalInputs().contains("timeRange"))) {
            input.putIfAbsent("timeRange", Map.of(
                    "startTime", range.startTime().toString(),
                    "endTime", range.endTime().toString(),
                    "hours", range.hours()
            ));
        }
        if (range != null && (definition.requiredInputs().contains("startTime")
                || definition.optionalInputs().contains("startTime")
                || definition.tags().contains("time"))) {
            input.putIfAbsent("startTime", range.startTime().toString());
            input.putIfAbsent("endTime", range.endTime().toString());
        }
        return input;
    }

    private boolean allowedByIntent(ToolDefinition definition, IntentLeafView intent) {
        if (definition.allowedIntentTypes() != null && !definition.allowedIntentTypes().isEmpty()
                && intent != null && intent.nodeCode() != null) {
            return definition.allowedIntentTypes().stream()
                    .anyMatch(code -> code.equalsIgnoreCase(intent.nodeCode()));
        }
        if (intent == null || intent.allowedToolTypes().isEmpty()) {
            return true;
        }
        Set<String> allowed = intent.allowedToolTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return allowed.contains(value(definition.toolType()).toLowerCase(Locale.ROOT))
                || allowed.contains(value(definition.platform()).toLowerCase(Locale.ROOT))
                || allowed.contains(prefix(definition.name()));
    }

    private double intentScore(ToolDefinition definition, IntentLeafView intent) {
        return allowedByIntent(definition, intent) ? 1.0 : 0.0;
    }

    private boolean duplicateSuccessful(String toolName, Map<String, Object> input, List<String> successfulKeys) {
        if (successfulKeys == null || successfulKeys.isEmpty()) {
            return false;
        }
        String key = key(toolName, input);
        return successfulKeys.stream().anyMatch(value -> Objects.equals(value, key));
    }

    public String key(String toolName, Map<String, Object> input) {
        return toolName + "|" + new java.util.TreeMap<>(input == null ? Map.of() : input);
    }

    private ToolCallValidation reject(ToolPlanCandidate candidate, ToolDefinition definition, String reason) {
        return new ToolCallValidation(false, candidate, definition, candidate.input(), reason, 0.0, 0.0, false);
    }

    private void putIfPresent(Map<String, Object> input, String key, Object value) {
        if (hasValue(value)) {
            input.putIfAbsent(key, value);
        }
    }

    private boolean parametersLookTyped(Map<String, Object> input) {
        for (Object value : input.values()) {
            if (value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof Map<?, ?> || value instanceof List<?> || value instanceof Instant) {
                continue;
            }
            if (value == null) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean hasValue(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String prefix(String toolName) {
        int index = toolName == null ? -1 : toolName.indexOf('.');
        return index < 0 ? value(toolName).toLowerCase(Locale.ROOT) : toolName.substring(0, index).toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
