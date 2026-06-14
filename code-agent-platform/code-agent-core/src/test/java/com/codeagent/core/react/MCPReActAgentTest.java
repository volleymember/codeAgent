package com.codeagent.core.react;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.parallel.ParallelAgentExecutionReport;
import com.codeagent.core.parallel.ParallelAgentExecutionService;
import com.codeagent.core.understanding.IntentClassificationResult;
import com.codeagent.core.understanding.ProjectContext;
import com.codeagent.core.understanding.QueryUnderstandingResult;
import com.codeagent.core.understanding.ResolvedTimeRange;
import com.codeagent.core.understanding.TimeRangeResolver;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.LlmResponse;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.router.McpRouter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MCPReActAgentTest {
    @Test
    void continuesWhenReflectionSaysEvidenceIsInsufficient() {
        StubLlm llm = new StubLlm(plan("jenkins.get_test_report"), reflection(false),
                plan("jenkins.get_build_status"), reflection(true));
        ParallelAgentExecutionService execution = mock(ParallelAgentExecutionService.class);
        when(execution.collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any()))
                .thenReturn(report("jenkins.get_test_report", true), report("jenkins.get_build_status", true));
        MCPReActAgent agent = agent(llm, execution, tools("jenkins.get_test_report", "jenkins.get_build_status"));

        MCPReActResult result = agent.execute(context(Map.of(), List.of("jenkins_log")));

        assertThat(result.rounds()).isEqualTo(2);
        assertThat(result.stoppedBySufficiency()).isTrue();
        verify(execution, org.mockito.Mockito.times(2)).collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any());
    }

    @Test
    void stopsWhenReflectionIsSufficient() {
        StubLlm llm = new StubLlm(plan("jenkins.get_test_report"), reflection(true));
        ParallelAgentExecutionService execution = mock(ParallelAgentExecutionService.class);
        when(execution.collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any()))
                .thenReturn(report("jenkins.get_test_report", true));
        MCPReActAgent agent = agent(llm, execution, tools("jenkins.get_test_report"));

        MCPReActResult result = agent.execute(context(Map.of(), List.of("jenkins_log")));

        assertThat(result.rounds()).isEqualTo(1);
        assertThat(result.stoppedBySufficiency()).isTrue();
        verify(execution).collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any());
    }

    @Test
    void doesNotExecuteIllegalToolPlan() {
        StubLlm llm = new StubLlm(plan("invented.tool"));
        ParallelAgentExecutionService execution = mock(ParallelAgentExecutionService.class);
        MCPReActAgent agent = agent(llm, execution, tools("jenkins.get_test_report"));

        MCPReActResult result = agent.execute(context(Map.of(), List.of("jenkins_log")));

        assertThat(result.executedPlans()).isEmpty();
        assertThat(result.rejectedToolCalls()).extracting(RejectedToolCall::rejectedReason)
                .contains("TOOL_NOT_FOUND");
        verify(execution, never()).collectEvidence(any(), any(), any(), any());
    }

    @Test
    void stopsAfterTwoRoundsWithoutNewEvidenceOrFacts() {
        AgentProperties properties = new AgentProperties();
        properties.getMcpReact().setMaxRounds(4);
        StubLlm llm = new StubLlm(plan("jenkins.get_test_report"), reflection(false),
                plan("jenkins.get_build_status"), reflection(false),
                plan("jenkins.get_failed_stage"), reflection(false));
        ParallelAgentExecutionService execution = mock(ParallelAgentExecutionService.class);
        when(execution.collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any()))
                .thenReturn(report("jenkins.get_test_report", false),
                        report("jenkins.get_build_status", false),
                        report("jenkins.get_failed_stage", false));
        MCPReActAgent agent = agent(llm, execution,
                tools("jenkins.get_test_report", "jenkins.get_build_status", "jenkins.get_failed_stage"), properties);

        MCPReActResult result = agent.execute(context(Map.of("toolName", "x", "status", "SUCCESS", "evidenceCount", 0),
                List.of("jenkins_log")));

        assertThat(result.rounds()).isEqualTo(2);
        verify(execution, org.mockito.Mockito.times(2)).collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any());
    }

    @Test
    void sendsOnlySandboxedObservationToReflection() {
        StubLlm llm = new StubLlm(plan("jenkins.get_test_report"), reflection(true));
        ParallelAgentExecutionService execution = mock(ParallelAgentExecutionService.class);
        when(execution.collectEvidence(eq("TASK-1"), eq("SESSION-1"), any(), any()))
                .thenReturn(reportWithSummary("jenkins.get_test_report", "token=abc123 IllegalStateException"));
        MCPReActAgent agent = agent(llm, execution, tools("jenkins.get_test_report"));

        agent.execute(context(Map.of(), List.of("jenkins_log")));

        String reflectionPrompt = llm.requests.stream()
                .filter(request -> request.taskType().name().equals("OBSERVATION_REFLECTION"))
                .findFirst()
                .orElseThrow()
                .userPrompt();
        assertThat(reflectionPrompt).contains("***MASKED***");
        assertThat(reflectionPrompt).doesNotContain("abc123");
    }

    private MCPReActAgent agent(StubLlm llm, ParallelAgentExecutionService execution, List<ToolDefinition> tools) {
        return agent(llm, execution, tools, new AgentProperties());
    }

    private MCPReActAgent agent(StubLlm llm,
                                ParallelAgentExecutionService execution,
                                List<ToolDefinition> tools,
                                AgentProperties properties) {
        McpRouter router = mock(McpRouter.class);
        when(router.listTools()).thenReturn(tools);
        return new MCPReActAgent(llm, router, execution,
                new ToolCallGuardrail(new TimeRangeResolver(properties)),
                new ToolScoreRanker(properties),
                new ToolOutputSandbox(),
                properties);
    }

    private InvestigationContext context(Map<String, Object> knownFacts, List<String> missingFacts) {
        CreateAgentTaskCommand command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment",
                null, "https://jenkins.example.com/job/payment/1", null, null, null, null);
        IntentLeafView leaf = new IntentLeafView("default", 1, "CI_FAILURE_ANALYSIS", "ROOT/CI",
                "CI", "CI", List.of(), List.of(), 24, List.of("jenkins"), List.of("jenkins_log"));
        ProjectContext project = new ProjectContext("payment", "payment", "repo", "1",
                "job", "sonar", "main", "logs", "apm", true, List.of());
        Instant end = Instant.parse("2026-06-12T00:00:00Z");
        return new InvestigationContext("TASK-1", "SESSION-1", command, query(),
                new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.9, List.of(),
                        List.of(), false, "", Map.of()), leaf,
                new ResolvedTimeRange(end.minusSeconds(3600), end, 1, "TEST", List.of()),
                project, null, knownFacts, missingFacts, List.of(), List.of(), List.of(), List.of(), "");
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("build failed", "build failed", List.of("build"), List.of(),
                List.of("payment"), List.of("payment"), "", "", "", "", "", "", List.of(), 0.1);
    }

    private List<ToolDefinition> tools(String... names) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (String name : names) {
            definitions.add(new ToolDefinition(name, "Jenkins", "Jenkins tool",
                    List.of("jenkinsBuildUrl"), 1000, List.of("jenkins"), 500, false));
        }
        return definitions;
    }

    private ParallelAgentExecutionReport report(String toolName, boolean withEvidence) {
        return reportWithSummary(toolName, withEvidence ? "ok" : "");
    }

    private ParallelAgentExecutionReport reportWithSummary(String toolName, String summary) {
        List<EvidenceItem> evidence = summary == null || summary.isBlank()
                ? List.of()
                : List.of(new EvidenceItem("jenkins_log", "Jenkins log", summary, 0.8, "uri", "raw", Map.of()));
        ToolCallResult result = new ToolCallResult(toolName, "SUCCESS", summary, "raw://" + toolName,
                evidence, null, 1);
        return new ParallelAgentExecutionReport("TASK-1", 1, 1, 0, 0, 1,
                List.of(), List.of(result), evidence, List.of(), Map.of());
    }

    private String plan(String toolName) {
        return """
                {
                  "reasoningSummary": "need tool",
                  "toolCalls": [
                    {
                      "toolName": "%s",
                      "purpose": "collect evidence",
                      "input": {"jenkinsBuildUrl": "https://jenkins.example.com/job/payment/1"},
                      "expectedOutput": ["jenkins_log"],
                      "priority": 1,
                      "whyNeeded": "required evidence"
                    }
                  ],
                  "stopCondition": "enough evidence",
                  "missingFacts": [],
                  "riskNotes": []
                }
                """.formatted(toolName);
    }

    private String reflection(boolean sufficient) {
        return """
                {
                  "sufficient": %s,
                  "confidence": %s,
                  "missingEvidence": %s,
                  "nextToolHints": [],
                  "finalReasoningSummary": "summary"
                }
                """.formatted(sufficient, sufficient ? "0.9" : "0.4",
                sufficient ? "[]" : "[\"jenkins_log\"]");
    }

    static class StubLlm implements LlmClient {
        private final Queue<String> responses = new ArrayDeque<>();
        private final List<LlmRequest> requests = new ArrayList<>();

        StubLlm(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            requests.add(request);
            return new LlmResponse("REQ", "deepseek-v4-pro", responses.remove(), 0, 0, 1);
        }
    }
}
