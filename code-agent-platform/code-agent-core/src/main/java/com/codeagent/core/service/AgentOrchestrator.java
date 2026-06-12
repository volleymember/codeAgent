package com.codeagent.core.service;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.enums.AgentStepStatus;
import com.codeagent.common.enums.TaskStatus;
import com.codeagent.common.exception.BusinessException;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.agent.CritiqueAgent;
import com.codeagent.core.agent.FinalReportAgent;
import com.codeagent.core.agent.PlannerAgent;
import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.parallel.ParallelAgentExecutionReport;
import com.codeagent.core.parallel.ParallelAgentExecutionService;
import com.codeagent.memory.MemoryCenterService;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.storage.entity.AgentSessionEntity;
import com.codeagent.storage.entity.AgentStepEntity;
import com.codeagent.storage.entity.AgentTaskEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import com.codeagent.storage.repository.AgentSessionRepository;
import com.codeagent.storage.repository.AgentStepRepository;
import com.codeagent.storage.repository.AgentTaskRepository;
import com.codeagent.storage.repository.EvidenceRecordRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AgentOrchestrator {
    private final AgentTaskRepository taskRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentStepRepository stepRepository;
    private final EvidenceRecordRepository evidenceRepository;
    private final PlannerAgent plannerAgent;
    private final CritiqueAgent critiqueAgent;
    private final FinalReportAgent finalReportAgent;
    private final ParallelAgentExecutionService parallelAgentExecutionService;
    private final MemoryCenterService memoryCenterService;
    private final ToolEvidenceRagIndexer toolEvidenceRagIndexer;
    private final AgentProperties properties;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public AgentOrchestrator(AgentTaskRepository taskRepository,
                             AgentSessionRepository sessionRepository,
                             AgentStepRepository stepRepository,
                             EvidenceRecordRepository evidenceRepository,
                             PlannerAgent plannerAgent,
                             CritiqueAgent critiqueAgent,
                             FinalReportAgent finalReportAgent,
                             ParallelAgentExecutionService parallelAgentExecutionService,
                             MemoryCenterService memoryCenterService,
                             ToolEvidenceRagIndexer toolEvidenceRagIndexer,
                             AgentProperties properties) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
        this.stepRepository = stepRepository;
        this.evidenceRepository = evidenceRepository;
        this.plannerAgent = plannerAgent;
        this.critiqueAgent = critiqueAgent;
        this.finalReportAgent = finalReportAgent;
        this.parallelAgentExecutionService = parallelAgentExecutionService;
        this.memoryCenterService = memoryCenterService;
        this.toolEvidenceRagIndexer = toolEvidenceRagIndexer;
        this.properties = properties;
    }

    public AgentTaskEntity createTask(CreateAgentTaskCommand command) {
        AgentTaskEntity task = new AgentTaskEntity();
        task.taskNo = nextTaskNo();
        task.taskType = command.taskType();
        task.projectKey = command.projectKey();
        task.userInput = JsonSupport.toJson(command);
        task.status = TaskStatus.CREATED.name();
        task.currentRound = 0;
        task.maxRounds = properties.getMaxRounds();
        task.requestJson = JsonSupport.toJson(command);
        task.createdAt = LocalDateTime.now();
        task.updatedAt = task.createdAt;
        taskRepository.save(task);

        AgentSessionEntity session = new AgentSessionEntity();
        session.sessionId = "SESSION-" + UUID.randomUUID();
        session.taskNo = task.taskNo;
        session.status = TaskStatus.CREATED.name();
        session.createdAt = LocalDateTime.now();
        session.updatedAt = session.createdAt;
        sessionRepository.save(session);

        executorService.submit(() -> runTask(task.taskNo, session.sessionId, command));
        return task;
    }

    public AgentTaskEntity getTask(String taskNo) {
        return taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("TASK_NOT_FOUND", "Task not found: " + taskNo));
    }

    public List<AgentStepEntity> getSteps(String taskNo) {
        return stepRepository.findByTaskNoOrderByIdAsc(taskNo);
    }

    public List<EvidenceRecordEntity> getEvidence(String taskNo) {
        return evidenceRepository.findByTaskNoOrderByIdAsc(taskNo);
    }

    private void runTask(String taskNo, String sessionId, CreateAgentTaskCommand command) {
        try {
            updateStatus(taskNo, sessionId, TaskStatus.CONTEXT_RESOLVING, null, 0);
            MemoryCenterContext memoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                    command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                    List.of(command.taskType(), command.projectKey()), 6,
                    taskNo, "MemoryCenter", "CONTEXT_RESOLVING"));
            memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "CONTEXT_RESOLVING",
                    "Loaded resident rules and recalled historical bug episodes.",
                    Map.of("coreRules", memoryContext.coreRules().size(),
                            "recalledEpisodes", memoryContext.recalledEpisodes().size()));

            updateStatus(taskNo, sessionId, TaskStatus.PLANNING, null, 0);
            List<ToolPlan> plans = plannerAgent.plan(command, memoryContext);
            AgentStepEntity planStep = startStep(taskNo, "PLAN-R1", "PlannerAgent", null, JsonSupport.toJson(command));
            finishStep(planStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(plannerAgent.describe(command, plans, memoryContext)), null);
            memoryCenterService.appendAgentNote(sessionId, "PlannerAgent", "PLANNING",
                    "Generated tool plan with Memory Center context.",
                    Map.of("toolCount", plans.size(), "requiredTools", plans.stream().filter(ToolPlan::required).count()));

            List<ToolPlan> executedPlans = new ArrayList<>();
            List<ToolCallResult> allResults = new ArrayList<>();
            List<EvidenceItem> evidence = new ArrayList<>();
            List<com.codeagent.core.parallel.AgentFinding> findings = new ArrayList<>();
            MemoryCenterContext finalMemoryContext = memoryContext;
            Map<String, Object> critique = Map.of();
            int nextEvidenceIndex = evidenceRepository.findByTaskNoOrderByIdAsc(taskNo).size() + 1;
            int maxRounds = Math.max(1, properties.getMaxRounds());
            for (int round = 1; round <= maxRounds; round++) {
                if (plans.isEmpty()) {
                    throw new BusinessException("NO_TOOL_PLAN", "No executable tool plan was generated from the task input.");
                }
                executedPlans.addAll(plans);

                updateStatus(taskNo, sessionId, TaskStatus.EXECUTING, null, round);
                ParallelAgentExecutionReport executionReport = parallelAgentExecutionService.collectEvidence(
                        taskNo, sessionId, command, plans);
                allResults.addAll(executionReport.toolResults());
                findings.addAll(executionReport.findings());

                updateStatus(taskNo, sessionId, TaskStatus.EVIDENCE_BUILDING, null, round);
                List<EvidenceItem> persistedEvidence = persistEvidence(taskNo, command.projectKey(), executionReport.evidence(), nextEvidenceIndex);
                nextEvidenceIndex += persistedEvidence.size();
                toolEvidenceRagIndexer.index(taskNo, command.projectKey(), persistedEvidence);
                evidence = mergeEvidence(evidence, persistedEvidence);
                memoryCenterService.updateWorkingContext(sessionId, Map.of(
                        "round", round,
                        "evidenceCount", evidence.size(),
                        "parallelSubmitted", executionReport.submittedCount(),
                        "successfulTools", executionReport.successfulCount(),
                        "failedTools", executionReport.failedCount(),
                        "requiredToolFailures", executionReport.requiredFailureCount(),
                        "agentFindingCount", findings.size(),
                        "parallelLatencyMs", executionReport.latencyMs()
                ));
                memoryCenterService.appendAgentNote(sessionId, "EvidenceBuilder", "EVIDENCE_BUILDING",
                        "Aggregated parallel agent evidence and updated shared working memory.",
                        executionReport.stats());

                finalMemoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                        command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                        evidenceSymptoms(evidence), 6,
                        taskNo, "MemoryCenter", "EVIDENCE_RECALL_R" + round));

                updateStatus(taskNo, sessionId, TaskStatus.CRITIQUING, null, round);
                critique = critiqueAgent.critique(executedPlans, allResults, evidence, finalMemoryContext, findings);
                AgentStepEntity critiqueStep = startStep(taskNo, "CRITIQUE-R" + round, "CritiqueAgent", null, JsonSupport.toJson(critique));
                finishStep(critiqueStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(critique), null);
                memoryCenterService.appendAgentNote(sessionId, "CritiqueAgent", "CRITIQUING",
                        "Evaluated evidence completeness with shared memory context.",
                        Map.of("round", round, "decision", critique.get("decision"), "confidence", critique.get("confidence")));

                if ("FINALIZE".equals(critique.get("decision"))) {
                    break;
                }
                if (round >= maxRounds) {
                    throw new BusinessException("EVIDENCE_INCOMPLETE", JsonSupport.toJson(critique));
                }
                updateStatus(taskNo, sessionId, TaskStatus.REPLANNING, null, round);
                plans = plannerAgent.replan(command, finalMemoryContext, critique, executedPlans, allResults, evidence, round + 1);
                AgentStepEntity replanStep = startStep(taskNo, "REPLAN-R" + (round + 1), "PlannerAgent", null, JsonSupport.toJson(critique));
                finishStep(replanStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(plannerAgent.describe(command, plans, finalMemoryContext)), null);
                memoryCenterService.appendAgentNote(sessionId, "PlannerAgent", "REPLANNING",
                        "Generated supplemental tool plan after critique.",
                        Map.of("nextRound", round + 1, "toolCount", plans.size()));
            }

            updateStatus(taskNo, sessionId, TaskStatus.FINALIZING, null, Math.max(1, getTask(taskNo).currentRound));
            String report = finalReportAgent.generate(taskNo, sessionId, command.taskType(), evidence, critique, finalMemoryContext);
            updateStatus(taskNo, sessionId, TaskStatus.COMPLETED, report, getTask(taskNo).currentRound);
            MemoryEpisodeMatch consolidated = memoryCenterService.consolidateBugExperience(
                    command.projectKey(), command.taskType(), taskNo, evidence, critique, report);
            memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "EPISODE_CONSOLIDATION",
                    "Consolidated completed task into episodic memory.",
                    Map.of("episodeId", consolidated == null || consolidated.episodeId() == null ? "N/A" : consolidated.episodeId()));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            updateStatus(taskNo, sessionId, TaskStatus.FAILED, "任务失败，未生成无证据结论。\n\n原因: " + message,
                    Math.max(0, getTask(taskNo).currentRound == null ? 0 : getTask(taskNo).currentRound));
        }
    }

    private AgentStepEntity startStep(String taskNo, String stepId, String agentName, String toolName, String inputJson) {
        AgentStepEntity step = new AgentStepEntity();
        step.taskNo = taskNo;
        step.stepId = stepId;
        step.agentName = agentName;
        step.toolName = toolName;
        step.status = AgentStepStatus.RUNNING.name();
        step.inputJson = inputJson;
        step.startedAt = LocalDateTime.now();
        return stepRepository.save(step);
    }

    private void finishStep(AgentStepEntity step, AgentStepStatus status, String outputSummary, String errorMessage) {
        step.status = status.name();
        step.outputSummary = outputSummary;
        step.errorMessage = errorMessage;
        step.finishedAt = LocalDateTime.now();
        stepRepository.save(step);
    }

    private List<EvidenceItem> persistEvidence(String taskNo, String projectKey, List<EvidenceItem> evidence, int startIndex) {
        List<EvidenceItem> persisted = new ArrayList<>();
        int index = Math.max(1, startIndex);
        for (EvidenceItem item : evidence) {
            EvidenceRecordEntity entity = new EvidenceRecordEntity();
            entity.evidenceNo = taskNo + "-E" + index++;
            entity.taskNo = taskNo;
            entity.projectKey = projectKey;
            entity.sourceSystem = item.sourceSystem();
            entity.sourceType = item.sourceType();
            entity.evidenceType = item.sourceType();
            entity.sourceUri = item.sourceUri();
            entity.sourceUrl = item.sourceUrl();
            entity.filePath = item.filePath();
            entity.rawRef = item.rawRef();
            entity.title = item.title();
            entity.summary = item.summary();
            entity.score = item.score();
            Map<String, Object> metadata = new LinkedHashMap<>(item.metadata());
            metadata.put("evidenceNo", entity.evidenceNo);
            metadata.put("originalRawRef", item.rawRef());
            entity.metadata = JsonSupport.toJson(metadata);
            entity.createdAt = LocalDateTime.now();
            evidenceRepository.save(entity);
            persisted.add(new EvidenceItem(
                    item.sourceType(),
                    item.sourceSystem(),
                    item.title(),
                    item.summary(),
                    item.score(),
                    item.sourceUri(),
                    item.sourceUrl(),
                    item.filePath(),
                    item.lineRange(),
                    entity.evidenceNo,
                    item.matchReason(),
                    metadata
            ));
        }
        return persisted;
    }

    private void updateStatus(String taskNo, String sessionId, TaskStatus status, String report, int round) {
        AgentTaskEntity task = getTask(taskNo);
        task.status = status.name();
        task.currentRound = Math.max(0, round);
        if (report != null) {
            task.finalReport = report;
        }
        task.updatedAt = LocalDateTime.now();
        taskRepository.save(task);
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                session.status = status.name();
                session.updatedAt = LocalDateTime.now();
                sessionRepository.save(session);
            });
        }
    }

    private String nextTaskNo() {
        return "TASK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String memoryQuery(CreateAgentTaskCommand command) {
        return "%s %s %s %s %s %s".formatted(
                command.taskType(),
                command.projectKey(),
                command.gitlabMrUrl(),
                command.jenkinsBuildUrl(),
                command.sonarqubeProjectKey(),
                command.jiraIssueKey()
        );
    }

    private List<String> evidenceSymptoms(List<EvidenceItem> evidence) {
        return evidence.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(8)
                .map(item -> "%s %s %s".formatted(item.title(), item.summary(), item.matchReason()))
                .toList();
    }

    private List<EvidenceItem> mergeEvidence(List<EvidenceItem> current, List<EvidenceItem> next) {
        Map<String, EvidenceItem> merged = new LinkedHashMap<>();
        for (EvidenceItem item : current == null ? List.<EvidenceItem>of() : current) {
            merged.put(evidenceKey(item), item);
        }
        for (EvidenceItem item : next == null ? List.<EvidenceItem>of() : next) {
            String key = evidenceKey(item);
            EvidenceItem existing = merged.get(key);
            if (existing == null || item.score() > existing.score()) {
                merged.put(key, item);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(EvidenceItem::score).reversed())
                .toList();
    }

    private String evidenceKey(EvidenceItem item) {
        return "%s|%s|%s|%s|%s".formatted(
                value(item.sourceSystem()),
                value(item.sourceUrl()),
                value(item.filePath()),
                value(item.lineRange()),
                value(item.title())
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }
}
