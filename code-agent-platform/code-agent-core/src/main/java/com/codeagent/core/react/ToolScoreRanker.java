package com.codeagent.core.react;

import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ToolScoreRanker {
    private final AgentProperties properties;

    public ToolScoreRanker(AgentProperties properties) {
        this.properties = properties;
    }

    public List<ToolPlan> rank(String stepPrefix,
                               List<ToolCallValidation> validations,
                               InvestigationContext context) {
        int maxTools = Math.max(1, properties.getMcpReact().getMaxToolsPerRound());
        List<ScoredTool> scored = validations.stream()
                .filter(ToolCallValidation::accepted)
                .map(validation -> new ScoredTool(validation, score(validation, context)))
                .sorted(Comparator.comparingDouble(ScoredTool::score).reversed()
                        .thenComparing(item -> item.validation().candidate().priority())
                        .thenComparing(item -> item.validation().candidate().toolName()))
                .toList();
        AtomicInteger highCost = new AtomicInteger();
        AtomicInteger index = new AtomicInteger(1);
        return scored.stream()
                .filter(item -> {
                    ToolDefinition definition = item.validation().definition();
                    if (!definition.highCost()) {
                        return true;
                    }
                    return highCost.incrementAndGet() <= 1;
                })
                .limit(maxTools)
                .map(item -> plan(stepPrefix + index.getAndIncrement(), item))
                .toList();
    }

    public double score(ToolCallValidation validation, InvestigationContext context) {
        ToolPlanCandidate candidate = validation.candidate();
        ToolDefinition definition = validation.definition();
        double llmPriorityScore = 1.0 / Math.max(1, candidate.priority());
        double missingFactsScore = intersects(candidate.expectedOutput(), context.missingFacts()) ? 1.0 : 0.35;
        double memoryHintScore = memoryHint(definition, context.memoryContext());
        double evidenceGapScore = evidenceGap(candidate, context);
        double costPenalty = definition.highCost() ? 0.12 : Math.min(0.10, definition.cost() / 100.0);
        double duplicatePenalty = validation.duplicate() ? 0.4 : 0.0;
        double score = 0.25 * llmPriorityScore
                + 0.20 * validation.intentMatchScore()
                + 0.20 * validation.inputCompletenessScore()
                + 0.15 * missingFactsScore
                + 0.10 * memoryHintScore
                + 0.10 * evidenceGapScore
                - costPenalty
                - duplicatePenalty;
        return Math.max(0.0, Math.min(1.0, score));
    }

    private ToolPlan plan(String stepId, ScoredTool item) {
        ToolCallValidation validation = item.validation();
        ToolDefinition definition = validation.definition();
        return new ToolPlan(stepId, agentName(definition.platform()), definition.name(), validation.resolvedInput(),
                true, item.score(), validation.candidate().whyNeeded(), definition.estimatedOutputTokens());
    }

    private double memoryHint(ToolDefinition definition, MemoryCenterContext memoryContext) {
        if (memoryContext == null || memoryContext.recalledEpisodes().isEmpty()) {
            return 0.3;
        }
        String haystack = (definition.name() + " " + definition.tags() + " " + definition.description()).toLowerCase(Locale.ROOT);
        boolean match = memoryContext.recalledEpisodes().stream()
                .limit(5)
                .anyMatch(episode -> haystack.contains(value(episode.rootCause()).toLowerCase(Locale.ROOT))
                        || value(episode.rootCause()).toLowerCase(Locale.ROOT).contains(prefix(definition.name())));
        return match ? 1.0 : 0.5;
    }

    private double evidenceGap(ToolPlanCandidate candidate, InvestigationContext context) {
        if (context.evidence().isEmpty()) {
            return 1.0;
        }
        return intersects(candidate.expectedOutput(), context.missingFacts()) ? 0.9 : 0.45;
    }

    private boolean intersects(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        List<String> normalizedRight = right.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
        return left.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedRight::contains);
    }

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

    private String prefix(String toolName) {
        int index = toolName == null ? -1 : toolName.indexOf('.');
        return index < 0 ? value(toolName).toLowerCase(Locale.ROOT) : toolName.substring(0, index).toLowerCase(Locale.ROOT);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record ScoredTool(ToolCallValidation validation, double score) {
    }
}
