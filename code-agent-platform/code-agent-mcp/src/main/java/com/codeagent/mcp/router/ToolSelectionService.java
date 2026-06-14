package com.codeagent.mcp.router;

import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.model.ToolRouteCandidate;
import com.codeagent.mcp.model.ToolRouteRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ToolSelectionService {
    private static final Set<String> CI_REQUIRED_TOOLS = Set.of(
            "jenkins.get_build_status",
            "jenkins.get_test_report",
            "jenkins.get_console_log_summary"
    );
    private static final Set<String> MR_REQUIRED_TOOLS = Set.of(
            "gitlab.get_merge_request",
            "gitlab.list_commits",
            "gitlab.get_merge_request_diff"
    );

    public List<ToolRouteCandidate> route(ToolRouteRequest request, List<ToolDefinition> definitions) {
        String intent = normalize("%s %s %s".formatted(request.taskType(), request.projectKey(), request.userGoal()));
        return definitions.stream()
                .map(definition -> score(request, definition, intent))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingDouble(ToolRouteCandidate::score).reversed()
                        .thenComparing(ToolRouteCandidate::estimatedOutputTokens)
                        .thenComparing(ToolRouteCandidate::toolName))
                .limit(request.maxTools())
                .toList();
    }

    private Optional<ToolRouteCandidate> score(ToolRouteRequest request, ToolDefinition definition, String intent) {
        Map<String, Object> input = resolveInput(definition, request.availableInputs());
        if (input.size() < definition.requiredInputs().size()) {
            return Optional.empty();
        }
        double score = platformScore(definition, request.availableInputs(), intent)
                + toolIntentScore(definition, intent)
                + tagScore(definition, intent);
        if (definition.highCost()) {
            score -= 0.04;
        }
        score = clamp(score);
        if (score < 0.28) {
            return Optional.empty();
        }
        boolean required = isRequired(definition.name(), intent);
        String reason = reason(definition, required, score);
        return Optional.of(new ToolRouteCandidate(definition.name(), definition.platform(), input, required,
                score, reason, definition.estimatedOutputTokens(), definition.highCost()));
    }

    private Map<String, Object> resolveInput(ToolDefinition definition, Map<String, Object> availableInputs) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (String requiredInput : definition.requiredInputs()) {
            Object value = availableInputs.get(requiredInput);
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            input.put(requiredInput, value);
        }
        return input;
    }

    private double platformScore(ToolDefinition definition, Map<String, Object> inputs, String intent) {
        String platform = normalize(definition.platform());
        if (platform.contains("jenkins") && (hasText(inputs.get("jenkinsJobName")) || hasText(inputs.get("jenkinsBuildUrl")))) {
            return intent.contains("ci") || intent.contains("fail") || intent.contains("build") ? 0.52 : 0.34;
        }
        if (platform.contains("gitlab") && (hasText(inputs.get("gitlabProjectId")) || hasText(inputs.get("gitlabMrUrl"))
                || hasText(inputs.get("mrIid")) || hasText(inputs.get("commitSha")))) {
            return intent.contains("mr") || intent.contains("merge") || intent.contains("risk") || intent.contains("code") ? 0.48 : 0.32;
        }
        if (platform.contains("sonarqube") && hasText(inputs.get("sonarqubeProjectKey"))) {
            return intent.contains("quality") || intent.contains("risk") || intent.contains("sonar") || intent.contains("code") ? 0.38 : 0.28;
        }
        return 0.0;
    }

    private double toolIntentScore(ToolDefinition definition, String intent) {
        String name = definition.name();
        if (intent.contains("ci") || intent.contains("fail") || intent.contains("build")) {
            if ("jenkins.get_test_report".equals(name) || "jenkins.get_console_log_summary".equals(name)) {
                return 0.38;
            }
            if ("jenkins.get_build_status".equals(name) || "jenkins.get_failed_stage".equals(name)) {
                return 0.34;
            }
            if ("gitlab.get_merge_request_diff".equals(name) || "gitlab.list_commits".equals(name)) {
                return 0.18;
            }
        }
        if (intent.contains("mr") || intent.contains("merge") || intent.contains("risk") || intent.contains("review")) {
            if ("gitlab.get_merge_request_diff".equals(name) || "gitlab.get_merge_request".equals(name)) {
                return 0.38;
            }
            if ("gitlab.list_commits".equals(name) || "gitlab.list_review_comments".equals(name)) {
                return 0.30;
            }
            if ("sonarqube.list_issues".equals(name) || "sonarqube.get_quality_gate".equals(name)) {
                return 0.20;
            }
        }
        if (intent.contains("quality") || intent.contains("coverage") || intent.contains("sonar")) {
            if (name.startsWith("sonarqube.")) {
                return 0.34;
            }
        }
        return 0.10;
    }

    private double tagScore(ToolDefinition definition, String intent) {
        double score = 0.0;
        for (String tag : definition.tags()) {
            if (intent.contains(tag)) {
                score += 0.05;
            }
        }
        return Math.min(0.14, score);
    }

    private boolean isRequired(String toolName, String intent) {
        if ((intent.contains("ci") || intent.contains("fail") || intent.contains("build")) && CI_REQUIRED_TOOLS.contains(toolName)) {
            return true;
        }
        return (intent.contains("mr") || intent.contains("merge") || intent.contains("risk")) && MR_REQUIRED_TOOLS.contains(toolName);
    }

    private String reason(ToolDefinition definition, boolean required, double score) {
        return "%s route score %.2f by platform=%s, tags=%s%s".formatted(
                definition.name(), score, definition.platform(), definition.tags(), required ? ", required evidence" : "");
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
