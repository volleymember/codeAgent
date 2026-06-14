package com.codeagent.core.service;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.core.agent.CritiqueAgent;
import com.codeagent.core.agent.FinalReportAgent;
import com.codeagent.core.agent.PlannerAgent;
import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.IntentTreeService;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.parallel.ParallelAgentExecutionReport;
import com.codeagent.core.parallel.ParallelAgentExecutionService;
import com.codeagent.core.react.MCPReActAgent;
import com.codeagent.core.react.MCPReActResult;
import com.codeagent.core.react.ObservationReflection;
import com.codeagent.core.react.EvidenceMatrixPlanner;
import com.codeagent.core.understanding.AmbiguityDecision;
import com.codeagent.core.understanding.IntentAmbiguityResolver;
import com.codeagent.core.understanding.IntentClassificationResult;
import com.codeagent.core.understanding.IntentClassifier;
import com.codeagent.core.understanding.ProjectContext;
import com.codeagent.core.understanding.ProjectContextResolver;
import com.codeagent.core.understanding.QueryUnderstandingResult;
import com.codeagent.core.understanding.QueryUnderstandingService;
import com.codeagent.core.understanding.ResolvedTimeRange;
import com.codeagent.core.understanding.TimeRangeResolver;
import com.codeagent.memory.MemoryCenterService;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.storage.entity.AgentSessionEntity;
import com.codeagent.storage.entity.AgentStepEntity;
import com.codeagent.storage.entity.AgentTaskEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import com.codeagent.storage.repository.AgentSessionRepository;
import com.codeagent.storage.repository.AgentStepRepository;
import com.codeagent.storage.repository.AgentTaskRepository;
import com.codeagent.storage.repository.EvidenceRecordRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {
    @Test
    void ambiguityStopsAtNeedsClarificationAndDoesNotExecuteTools() throws Exception {
        Harness h = harness(new AgentProperties());
        when(h.ambiguityResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(new AmbiguityDecision(true, "LOW_CONFIDENCE", "请确认任务类型。"));
        when(h.ambiguityResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AmbiguityDecision(true, "LOW_CONFIDENCE", "请确认任务类型。"));

        h.run();

        assertThat(h.task.status).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(h.task.finalReport).isEqualTo("请确认任务类型。");
        verify(h.mcpReActAgent, never()).execute(any());
        verify(h.parallelAgentExecutionService, never()).collectEvidence(any(), any(), any(), anyList());
    }

    @Test
    void missingProjectOrServiceNeedsClarification() throws Exception {
        Harness h = harness(new AgentProperties());
        h.command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", null, null, null,
                null, null, null, null, "构建失败", null, null, null, null, null);
        when(h.queryUnderstandingService.understand(any(), any(), any())).thenReturn(new QueryUnderstandingResult(
                "构建失败", "构建失败", List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", "", "", List.of(), 0.2));
        when(h.projectContextResolver.resolve(any(), any())).thenReturn(new ProjectContext(null, null,
                null, null, null, null, null, null, null, false, List.of("projectKeyOrServiceName")));
        when(h.ambiguityResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
                .thenCallRealMethod();

        h.run();

        assertThat(h.task.status).isEqualTo("NEEDS_CLARIFICATION");
        verify(h.mcpReActAgent, never()).execute(any());
    }

    @Test
    void missingJenkinsBuildUrlDoesNotNeedClarificationWhenProjectHasJenkinsConfig() throws Exception {
        Harness h = harness(new AgentProperties());
        h.command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment", null, null,
                null, null, null, null, "构建失败", null, null, null, null, null);
        when(h.mcpReActAgent.execute(any())).thenReturn(reactResult());
        when(h.critiqueAgent.critique(anyList(), anyList(), anyList(), any(), anyList()))
                .thenReturn(Map.of("decision", "FINALIZE", "confidence", 0.9, "missingEvidence", List.of()));
        when(h.finalReportAgent.generate(any(), any(), any(), anyList(), any(), any())).thenReturn("report");

        h.run();

        assertThat(h.task.status).isEqualTo("COMPLETED");
        verify(h.mcpReActAgent).execute(any());
    }

    @Test
    void clearIntentUsesMcpReactWhenEnabled() throws Exception {
        Harness h = harness(new AgentProperties());
        when(h.mcpReActAgent.execute(any())).thenReturn(reactResult());
        when(h.critiqueAgent.critique(anyList(), anyList(), anyList(), any(), anyList()))
                .thenReturn(Map.of("decision", "FINALIZE", "confidence", 0.9, "missingEvidence", List.of()));
        when(h.finalReportAgent.generate(any(), any(), any(), anyList(), any(), any())).thenReturn("report");

        h.run();

        assertThat(h.task.status).isEqualTo("COMPLETED");
        assertThat(h.task.finalReport).isEqualTo("report");
        verify(h.mcpReActAgent).execute(any());
        verify(h.plannerAgent, never()).plan(any(), any());
    }

    @Test
    void disabledMcpReactFallsBackToPlannerAgent() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.getMcpReact().setEnabled(false);
        Harness h = harness(properties);
        ToolPlan plan = new ToolPlan("S1", "CIAnalysisAgent", "jenkins.get_test_report",
                Map.of("jenkinsBuildUrl", h.command.jenkinsBuildUrl()), true, 0.9, "test", 500);
        when(h.plannerAgent.plan(any(), any())).thenReturn(List.of(plan));
        when(h.plannerAgent.describe(any(), anyList(), any())).thenReturn(Map.of("steps", List.of(plan)));
        when(h.parallelAgentExecutionService.collectEvidence(any(), any(), any(), anyList())).thenReturn(report());
        when(h.critiqueAgent.critique(anyList(), anyList(), anyList(), any(), anyList()))
                .thenReturn(Map.of("decision", "FINALIZE", "confidence", 0.9, "missingEvidence", List.of()));
        when(h.finalReportAgent.generate(any(), any(), any(), anyList(), any(), any())).thenReturn("legacy report");

        h.run();

        assertThat(h.task.status).isEqualTo("COMPLETED");
        verify(h.plannerAgent).plan(any(), any());
        verify(h.mcpReActAgent, never()).execute(any());
    }

    private Harness harness(AgentProperties properties) {
        Harness h = new Harness();
        h.properties = properties;
        h.taskRepository = mock(AgentTaskRepository.class);
        h.sessionRepository = mock(AgentSessionRepository.class);
        h.stepRepository = mock(AgentStepRepository.class);
        h.evidenceRepository = mock(EvidenceRecordRepository.class);
        h.plannerAgent = mock(PlannerAgent.class);
        h.queryUnderstandingService = mock(QueryUnderstandingService.class);
        h.intentTreeService = mock(IntentTreeService.class);
        h.intentClassifier = mock(IntentClassifier.class);
        h.ambiguityResolver = mock(IntentAmbiguityResolver.class);
        h.timeRangeResolver = mock(TimeRangeResolver.class);
        h.projectContextResolver = mock(ProjectContextResolver.class);
        h.mcpReActAgent = mock(MCPReActAgent.class);
        h.evidenceMatrixPlanner = new EvidenceMatrixPlanner();
        h.critiqueAgent = mock(CritiqueAgent.class);
        h.finalReportAgent = mock(FinalReportAgent.class);
        h.parallelAgentExecutionService = mock(ParallelAgentExecutionService.class);
        h.memoryCenterService = mock(MemoryCenterService.class);
        h.toolEvidenceRagIndexer = mock(ToolEvidenceRagIndexer.class);
        h.command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment", null,
                "https://jenkins.example.com/job/payment/1", null, null, null, null);
        h.task = new AgentTaskEntity();
        h.task.taskNo = "TASK-1";
        h.task.taskType = "CI_FAILURE_ANALYSIS";
        h.task.projectKey = "payment";
        h.task.currentRound = 0;
        h.session = new AgentSessionEntity();
        h.session.sessionId = "SESSION-1";
        when(h.taskRepository.findByTaskNo("TASK-1")).thenReturn(Optional.of(h.task));
        when(h.taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(h.sessionRepository.findBySessionId("SESSION-1")).thenReturn(Optional.of(h.session));
        when(h.sessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(h.stepRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(h.evidenceRepository.findByTaskNoOrderByIdAsc("TASK-1")).thenReturn(List.of());
        when(h.evidenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(h.queryUnderstandingService.understand(any(), any(), any())).thenReturn(query());
        when(h.intentTreeService.activeLeaves()).thenReturn(List.of(leaf()));
        when(h.intentClassifier.classify(any(), any(), any(), any(), anyList(), anyList(), anyString()))
                .thenReturn(classification());
        when(h.ambiguityResolver.resolve(any(), any(), any(), any(), any()))
                .thenReturn(new AmbiguityDecision(false, "", ""));
        when(h.ambiguityResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AmbiguityDecision(false, "", ""));
        when(h.memoryCenterService.buildContext(any())).thenReturn(memory());
        when(h.timeRangeResolver.resolve(any(), any(), any())).thenReturn(range());
        when(h.projectContextResolver.resolve(any(), any())).thenReturn(project());
        h.orchestrator = new AgentOrchestrator(h.taskRepository, h.sessionRepository, h.stepRepository,
                h.evidenceRepository, h.plannerAgent, h.queryUnderstandingService, h.intentTreeService,
                h.intentClassifier, h.ambiguityResolver, h.timeRangeResolver, h.projectContextResolver,
                h.mcpReActAgent, h.evidenceMatrixPlanner, h.critiqueAgent, h.finalReportAgent, h.parallelAgentExecutionService,
                h.memoryCenterService, h.toolEvidenceRagIndexer, properties);
        return h;
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("build failed", "build failed", List.of("build"), List.of(),
                List.of("payment"), List.of("payment"), "", "", "", "", "", "", List.of(), 0.1);
    }

    private IntentLeafView leaf() {
        return new IntentLeafView("default", 1, "CI_FAILURE_ANALYSIS", "ROOT/CI",
                "CI", "CI", List.of(), List.of(), 24, List.of("jenkins"), List.of("jenkins_log"));
    }

    private IntentClassificationResult classification() {
        return new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.9, List.of(),
                List.of(), false, "", Map.of());
    }

    private MemoryCenterContext memory() {
        return new MemoryCenterContext("SESSION-1", "payment", List.of(), Map.of(), List.of(), List.of());
    }

    private ProjectContext project() {
        return new ProjectContext("payment", "payment", "repo", "1", "job", "sonar",
                "main", "logs", "apm", true, List.of());
    }

    private ResolvedTimeRange range() {
        Instant end = Instant.parse("2026-06-12T00:00:00Z");
        return new ResolvedTimeRange(end.minusSeconds(3600), end, 1, "TEST", List.of());
    }

    private MCPReActResult reactResult() {
        EvidenceItem evidence = evidence();
        ToolCallResult result = new ToolCallResult("jenkins.get_test_report", "SUCCESS", "ok",
                "raw://1", List.of(evidence), null, 1);
        ToolPlan plan = new ToolPlan("RA1-1", "CIAnalysisAgent", "jenkins.get_test_report",
                Map.of("jenkinsBuildUrl", "https://jenkins.example.com/job/payment/1"), true);
        return new MCPReActResult(List.of(plan), List.of(result), List.of(evidence), List.of(),
                List.of(), Map.of(), List.of(), new ObservationReflection(true, 0.9, List.of(), List.of(), ""),
                1, true);
    }

    private ParallelAgentExecutionReport report() {
        EvidenceItem evidence = evidence();
        ToolCallResult result = new ToolCallResult("jenkins.get_test_report", "SUCCESS", "ok",
                "raw://1", List.of(evidence), null, 1);
        return new ParallelAgentExecutionReport("TASK-1", 1, 1, 0, 0, 1, List.of(),
                List.of(result), List.of(evidence), List.of(), Map.of());
    }

    private EvidenceItem evidence() {
        return new EvidenceItem("jenkins_log", "Jenkins log", "build failed", 0.9,
                "uri", "raw", Map.of());
    }

    static class Harness {
        AgentTaskRepository taskRepository;
        AgentSessionRepository sessionRepository;
        AgentStepRepository stepRepository;
        EvidenceRecordRepository evidenceRepository;
        PlannerAgent plannerAgent;
        QueryUnderstandingService queryUnderstandingService;
        IntentTreeService intentTreeService;
        IntentClassifier intentClassifier;
        IntentAmbiguityResolver ambiguityResolver;
        TimeRangeResolver timeRangeResolver;
        ProjectContextResolver projectContextResolver;
        MCPReActAgent mcpReActAgent;
        EvidenceMatrixPlanner evidenceMatrixPlanner;
        CritiqueAgent critiqueAgent;
        FinalReportAgent finalReportAgent;
        ParallelAgentExecutionService parallelAgentExecutionService;
        MemoryCenterService memoryCenterService;
        ToolEvidenceRagIndexer toolEvidenceRagIndexer;
        AgentProperties properties;
        CreateAgentTaskCommand command;
        AgentTaskEntity task;
        AgentSessionEntity session;
        AgentOrchestrator orchestrator;

        void run() throws Exception {
            Method method = AgentOrchestrator.class.getDeclaredMethod("runTask", String.class, String.class,
                    CreateAgentTaskCommand.class);
            method.setAccessible(true);
            method.invoke(orchestrator, "TASK-1", "SESSION-1", command);
        }
    }
}
