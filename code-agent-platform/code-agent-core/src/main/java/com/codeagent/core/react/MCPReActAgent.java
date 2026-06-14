package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.parallel.AgentFinding;
import com.codeagent.core.parallel.ParallelAgentExecutionReport;
import com.codeagent.core.parallel.ParallelAgentExecutionService;
import com.codeagent.core.understanding.LlmJsonSupport;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.router.McpRouter;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class MCPReActAgent {
    private final LlmClient llmClient;
    private final McpRouter mcpRouter;
    private final ParallelAgentExecutionService parallelAgentExecutionService;
    private final ToolCallGuardrail guardrail;
    private final ToolScoreRanker ranker;
    private final ToolOutputSandbox sandbox;
    private final EvidenceMatrixPlanner evidenceMatrixPlanner;
    private final AgentProperties properties;

    public MCPReActAgent(LlmClient llmClient,
                         McpRouter mcpRouter,
                         ParallelAgentExecutionService parallelAgentExecutionService,
                         ToolCallGuardrail guardrail,
                         ToolScoreRanker ranker,
                         ToolOutputSandbox sandbox,
                         EvidenceMatrixPlanner evidenceMatrixPlanner,
                         AgentProperties properties) {
        this.llmClient = llmClient;
        this.mcpRouter = mcpRouter;
        this.parallelAgentExecutionService = parallelAgentExecutionService;
        this.guardrail = guardrail;
        this.ranker = ranker;
        this.sandbox = sandbox;
        this.evidenceMatrixPlanner = evidenceMatrixPlanner;
        this.properties = properties;
    }

    public MCPReActResult execute(InvestigationContext initialContext) {
        List<ToolDefinition> definitions = mcpRouter.listTools();
        List<ToolPlan> executedPlans = new ArrayList<>();
        List<ToolCallResult> toolResults = new ArrayList<>();
        List<EvidenceItem> evidence = new ArrayList<>();
        List<AgentFinding> findings = new ArrayList<>();
        List<RejectedToolCall> rejected = new ArrayList<>(initialContext.rejectedToolCalls());
        Map<String, Object> knownFacts = new LinkedHashMap<>(initialContext.knownFacts());
        List<String> missingFacts = new ArrayList<>(initialContext.missingFacts());
        List<String> successfulToolCallKeys = new ArrayList<>(initialContext.successfulToolCallKeys());
        ObservationReflection reflection = new ObservationReflection(false, 0.0, missingFacts, List.of(), "");
        int stagnantRounds = 0;
        int totalToolCalls = 0;
        int rounds = 0;
        boolean stoppedBySufficiency = false;

        int maxRounds = Math.max(1, properties.getMcpReact().getMaxRounds());
        int maxTotal = Math.max(1, properties.getMcpReact().getMaxTotalToolCalls());
        for (int round = 1; round <= maxRounds && totalToolCalls < maxTotal; round++) {
            rounds = round;
            InvestigationContext context = context(initialContext, knownFacts, missingFacts, toolResults,
                    rejected, successfulToolCallKeys, evidence);
            List<ToolPlanCandidate> candidates = new ArrayList<>(evidenceMatrixPlanner.bootstrapCandidates(context, definitions));
            candidates.addAll(plan(context, definitions, rejected));
            List<ToolCallValidation> validations = candidates.stream()
                    .map(candidate -> guardrail.validate(candidate, definitions, context))
                    .toList();
            validations.stream()
                    .filter(validation -> !validation.accepted())
                    .map(validation -> new RejectedToolCall(validation.candidate().toolName(),
                            validation.resolvedInput(), validation.rejectedReason()))
                    .forEach(rejected::add);

            List<ToolPlan> plans = ranker.rank("RA" + round + "-", validations, context).stream()
                    .limit(maxTotal - totalToolCalls)
                    .toList();
            if (plans.isEmpty()) {
                break;
            }

            int factsBefore = knownFacts.size();
            int evidenceBefore = evidence.size();
            ParallelAgentExecutionReport report = parallelAgentExecutionService.collectEvidence(
                    initialContext.taskNo(), initialContext.sessionId(), initialContext.command(), plans);
            totalToolCalls += plans.size();
            executedPlans.addAll(plans);
            toolResults.addAll(report.toolResults());
            successfulToolCallKeys.addAll(successfulKeys(plans, report.toolResults()));
            findings.addAll(report.findings());
            evidence.addAll(report.evidence());

            List<CompressedToolObservation> observations = report.toolResults().stream()
                    .map(sandbox::compress)
                    .toList();
            for (CompressedToolObservation observation : observations) {
                knownFacts.putAll(observation.extractedFacts());
            }
            reflection = reflect(context(initialContext, knownFacts, missingFacts, toolResults,
                            rejected, successfulToolCallKeys, evidence),
                    observations);
            missingFacts = new ArrayList<>(reflection.missingEvidence());
            if (reflection.sufficient() && reflection.confidence() >= properties.getMinConfidence()) {
                stoppedBySufficiency = true;
                break;
            }
            boolean noProgress = knownFacts.size() == factsBefore && evidence.size() == evidenceBefore;
            stagnantRounds = noProgress ? stagnantRounds + 1 : 0;
            if (stagnantRounds >= 2) {
                break;
            }
        }

        return new MCPReActResult(executedPlans, toolResults, evidence, findings, rejected, knownFacts,
                missingFacts, reflection, rounds, stoppedBySufficiency);
    }

    private List<ToolPlanCandidate> plan(InvestigationContext context,
                                         List<ToolDefinition> definitions,
                                         List<RejectedToolCall> rejected) {
        String systemPrompt = """
                You are CodeAgent MCPReActAgent planner.
                Generate candidate MCP tool calls as strict JSON only.
                You cannot execute tools. The rule engine will validate, rank and execute.
                Use only toolName values from availableTools.
                Do not repeat already executed successful tools unless new evidence is necessary.
                Output:
                {
                  "reasoningSummary": "",
                  "toolCalls": [
                    {
                      "toolName": "",
                      "purpose": "",
                      "input": {},
                      "expectedOutput": [],
                      "priority": 1,
                      "whyNeeded": ""
                    }
                  ],
                  "stopCondition": "",
                  "missingFacts": [],
                  "riskNotes": []
                }
                """;
        Map<String, Object> promptPayload = new LinkedHashMap<>();
        promptPayload.put("originalQuery", context.queryUnderstanding().originalQuery());
        promptPayload.put("queryUnderstanding", context.queryUnderstanding());
        promptPayload.put("intentClassification", context.intentClassification());
        promptPayload.put("knownFacts", context.knownFacts());
        promptPayload.put("missingFacts", context.missingFacts());
        promptPayload.put("intentLeaf", context.intentLeaf());
        promptPayload.put("coreRules", coreRules(context.memoryContext()));
        promptPayload.put("recalledEpisodes", context.memoryContext() == null ? List.of() : context.memoryContext().recalledEpisodes());
        promptPayload.put("recentTurns", context.recentTurns());
        promptPayload.put("compressedSummary", context.compressedSummary());
        promptPayload.put("availableTools", definitions);
        promptPayload.put("executedTools", context.previousToolCalls().stream().map(ToolCallResult::toolName).toList());
        promptPayload.put("existingEvidence", context.evidence().stream().map(EvidenceItem::title).toList());
        promptPayload.put("rejectedToolCalls", rejected);
        String userPrompt = JsonSupport.toJson(promptPayload);
        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(context.taskNo(), context.sessionId(),
                ModelTaskType.REACT_TOOL_PLANNING, systemPrompt, userPrompt, 2600, 0.2)).content());
        JsonNode toolCalls = node.path("toolCalls");
        if (!toolCalls.isArray()) {
            return List.of();
        }
        List<ToolPlanCandidate> candidates = new ArrayList<>();
        for (JsonNode item : toolCalls) {
            candidates.add(new ToolPlanCandidate(
                    item.path("toolName").asText(""),
                    item.path("purpose").asText(""),
                    map(item.path("input")),
                    stringList(item.path("expectedOutput")),
                    item.path("priority").asInt(10),
                    item.path("whyNeeded").asText("")
            ));
        }
        return candidates;
    }

    private ObservationReflection reflect(InvestigationContext context, List<CompressedToolObservation> observations) {
        String systemPrompt = """
                You are CodeAgent observation reflector.
                You only see sanitized compressed tool observations. Never assume raw hidden data.
                Decide whether evidence is sufficient and what to investigate next.
                Output strict JSON only:
                {
                  "sufficient": false,
                  "confidence": 0.0,
                  "missingEvidence": [],
                  "nextToolHints": [],
                  "finalReasoningSummary": ""
                }
                """;
        String userPrompt = JsonSupport.toJson(Map.of(
                "intent", context.intentClassification(),
                "knownFacts", context.knownFacts(),
                "existingEvidenceCount", context.evidence().size(),
                "observations", observations,
                "previousRejectedToolCalls", context.rejectedToolCalls()
        ));
        JsonNode node = LlmJsonSupport.parseObject(llmClient.chat(new LlmRequest(context.taskNo(), context.sessionId(),
                ModelTaskType.OBSERVATION_REFLECTION, systemPrompt, userPrompt, 1600, 0.2)).content());
        return new ObservationReflection(
                node.path("sufficient").asBoolean(false),
                node.path("confidence").asDouble(0.0),
                stringList(node.path("missingEvidence")),
                stringList(node.path("nextToolHints")),
                node.path("finalReasoningSummary").asText("")
        );
    }

    private InvestigationContext context(InvestigationContext base,
                                         Map<String, Object> knownFacts,
                                         List<String> missingFacts,
                                         List<ToolCallResult> previousToolCalls,
                                         List<RejectedToolCall> rejected,
                                         List<String> successfulToolCallKeys,
                                         List<EvidenceItem> evidence) {
        return new InvestigationContext(base.taskNo(), base.sessionId(), base.command(), base.queryUnderstanding(),
                base.intentClassification(), base.intentLeaf(), base.timeRange(), base.projectContext(), base.memoryContext(),
                knownFacts, distinct(missingFacts), previousToolCalls, rejected, successfulToolCallKeys, evidence,
                base.recentTurns(), base.compressedSummary());
    }

    private List<String> successfulKeys(List<ToolPlan> plans, List<ToolCallResult> results) {
        List<String> keys = new ArrayList<>();
        for (ToolPlan plan : plans) {
            boolean success = results.stream()
                    .anyMatch(result -> plan.toolName().equals(result.toolName()) && "SUCCESS".equals(result.status()));
            if (success) {
                keys.add(guardrail.key(plan.toolName(), plan.input()));
            }
        }
        return keys;
    }

    private List<?> coreRules(MemoryCenterContext memoryContext) {
        return memoryContext == null ? List.of() : new ArrayList<>(memoryContext.coreRules());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        return JsonSupport.mapper().convertValue(node, LinkedHashMap.class);
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return JsonSupport.mapper().convertValue(node,
                JsonSupport.mapper().getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private List<String> distinct(List<String> values) {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }
}
