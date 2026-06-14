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
import com.codeagent.core.intent.IntentTreeService;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.parallel.ParallelAgentExecutionReport;
import com.codeagent.core.parallel.ParallelAgentExecutionService;
import com.codeagent.core.react.InvestigationContext;
import com.codeagent.core.react.MCPReActAgent;
import com.codeagent.core.react.MCPReActResult;
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

/**
 * Agent 任务编排服务。
 *
 * <p>该服务是 Agent 执行链路的总入口，负责从任务创建开始，串联查询理解、意图识别、
 * 歧义判断、上下文召回、工具规划、工具执行、证据构建、证据评审、报告生成和经验沉淀。</p>
 *
 * <p>当前类同时支持两套执行模式：</p>
 * <ul>
 *     <li>传统模式：PlannerAgent 生成工具计划，ParallelAgentExecutionService 并行采集证据。</li>
 *     <li>MCP ReAct 模式：MCPReActAgent 进行多轮“思考-行动-观察-反思”的闭环调查。</li>
 * </ul>
 *
 * <p>任务创建后会异步提交到虚拟线程池中执行，创建接口会立即返回任务实体，
 * 调用方可通过 taskNo 查询任务状态、步骤和证据。</p>
 */
@Service
public class AgentOrchestrator {

    /** Agent 任务仓储，用于创建、查询和更新任务状态。 */
    private final AgentTaskRepository taskRepository;

    /** Agent 会话仓储，用于维护一次任务执行过程中的会话状态。 */
    private final AgentSessionRepository sessionRepository;

    /** Agent 步骤仓储，用于记录规划、评审等关键执行步骤。 */
    private final AgentStepRepository stepRepository;

    /** 证据记录仓储，用于持久化工具执行过程中采集到的证据。 */
    private final EvidenceRecordRepository evidenceRepository;

    /** 规划 Agent，负责在传统执行模式下生成工具执行计划。 */
    private final PlannerAgent plannerAgent;

    /** 查询理解服务，用于从原始任务输入中提取 traceId、commit、branch、错误信息等结构化字段。 */
    private final QueryUnderstandingService queryUnderstandingService;

    /** 意图树服务，用于提供当前系统启用的意图叶子节点集合。 */
    private final IntentTreeService intentTreeService;

    /** 意图分类器，用于将用户查询映射到最匹配的业务意图。 */
    private final IntentClassifier intentClassifier;

    /** 歧义解析器，用于判断当前任务是否需要用户补充信息。 */
    private final IntentAmbiguityResolver ambiguityResolver;

    /** 时间范围解析器，用于推导日志、构建、监控等工具查询时需要使用的时间窗口。 */
    private final TimeRangeResolver timeRangeResolver;

    /** 项目上下文解析器，用于解析项目、服务、MR、构建、Jira、SonarQube 等上下文。 */
    private final ProjectContextResolver projectContextResolver;

    /** MCP ReAct Agent，用于在启用 ReAct 模式时执行多轮工具调查。 */
    private final MCPReActAgent mcpReActAgent;

    /** 评审 Agent，负责判断当前证据是否足够支撑最终结论。 */
    private final CritiqueAgent critiqueAgent;

    /** 最终报告 Agent，负责基于证据和评审结果生成最终报告。 */
    private final FinalReportAgent finalReportAgent;

    /** 并行工具执行服务，负责在传统模式下并发调用多个工具采集证据。 */
    private final ParallelAgentExecutionService parallelAgentExecutionService;

    /** Memory Center 服务，负责上下文召回、工作记忆更新和经验沉淀。 */
    private final MemoryCenterService memoryCenterService;

    /** 工具证据 RAG 索引器，负责将工具采集到的证据写入 RAG 索引。 */
    private final ToolEvidenceRagIndexer toolEvidenceRagIndexer;

    /** Agent 执行配置，例如最大轮次、是否启用 MCP ReAct 等。 */
    private final AgentProperties properties;

    /**
     * 异步任务执行器。
     *
     * <p>使用虚拟线程执行每个 Agent 任务，适合包含大量 I/O 调用的工具执行流程。</p>
     */
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建 Agent 任务编排服务。
     *
     * @param taskRepository                任务仓储
     * @param sessionRepository             会话仓储
     * @param stepRepository                步骤仓储
     * @param evidenceRepository            证据仓储
     * @param plannerAgent                  规划 Agent
     * @param queryUnderstandingService     查询理解服务
     * @param intentTreeService             意图树服务
     * @param intentClassifier              意图分类器
     * @param ambiguityResolver             歧义解析器
     * @param timeRangeResolver             时间范围解析器
     * @param projectContextResolver        项目上下文解析器
     * @param mcpReActAgent                 MCP ReAct Agent
     * @param critiqueAgent                 评审 Agent
     * @param finalReportAgent              最终报告 Agent
     * @param parallelAgentExecutionService 并行工具执行服务
     * @param memoryCenterService           Memory Center 服务
     * @param toolEvidenceRagIndexer        工具证据 RAG 索引器
     * @param properties                    Agent 配置
     */
    public AgentOrchestrator(AgentTaskRepository taskRepository,
                             AgentSessionRepository sessionRepository,
                             AgentStepRepository stepRepository,
                             EvidenceRecordRepository evidenceRepository,
                             PlannerAgent plannerAgent,
                             QueryUnderstandingService queryUnderstandingService,
                             IntentTreeService intentTreeService,
                             IntentClassifier intentClassifier,
                             IntentAmbiguityResolver ambiguityResolver,
                             TimeRangeResolver timeRangeResolver,
                             ProjectContextResolver projectContextResolver,
                             MCPReActAgent mcpReActAgent,
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
        this.queryUnderstandingService = queryUnderstandingService;
        this.intentTreeService = intentTreeService;
        this.intentClassifier = intentClassifier;
        this.ambiguityResolver = ambiguityResolver;
        this.timeRangeResolver = timeRangeResolver;
        this.projectContextResolver = projectContextResolver;
        this.mcpReActAgent = mcpReActAgent;
        this.critiqueAgent = critiqueAgent;
        this.finalReportAgent = finalReportAgent;
        this.parallelAgentExecutionService = parallelAgentExecutionService;
        this.memoryCenterService = memoryCenterService;
        this.toolEvidenceRagIndexer = toolEvidenceRagIndexer;
        this.properties = properties;
    }

    /**
     * 创建 Agent 任务并异步启动执行。
     *
     * <p>该方法会先创建任务记录和会话记录，然后将实际执行逻辑提交到线程池。
     * 调用方可以通过返回的 taskNo 查询后续执行状态、步骤和证据。</p>
     *
     * @param command 创建 Agent 任务的命令参数
     * @return 已创建的任务实体
     */
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

        // 异步执行任务，避免创建接口被长时间工具调用阻塞。
        executorService.submit(() -> runTask(task.taskNo, session.sessionId, command));
        return task;
    }

    /**
     * 根据任务编号查询任务。
     *
     * @param taskNo 任务编号
     * @return 任务实体
     * @throws BusinessException 当任务不存在时抛出
     */
    public AgentTaskEntity getTask(String taskNo) {
        return taskRepository.findByTaskNo(taskNo)
                .orElseThrow(() -> new BusinessException("TASK_NOT_FOUND", "Task not found: " + taskNo));
    }

    /**
     * 查询指定任务的执行步骤。
     *
     * @param taskNo 任务编号
     * @return 按 ID 升序排列的步骤列表
     */
    public List<AgentStepEntity> getSteps(String taskNo) {
        return stepRepository.findByTaskNoOrderByIdAsc(taskNo);
    }

    /**
     * 查询指定任务的证据记录。
     *
     * @param taskNo 任务编号
     * @return 按 ID 升序排列的证据列表
     */
    public List<EvidenceRecordEntity> getEvidence(String taskNo) {
        return evidenceRepository.findByTaskNoOrderByIdAsc(taskNo);
    }

    /**
     * 执行 Agent 任务主流程。
     *
     * <p>流程包括查询理解、意图分类、歧义检查、Memory Center 上下文加载，
     * 然后根据配置选择传统工具规划流程或 MCP ReAct 流程。</p>
     *
     * @param taskNo    任务编号
     * @param sessionId 会话编号
     * @param command   任务创建命令
     */
    private void runTask(String taskNo, String sessionId, CreateAgentTaskCommand command) {
        try {
            // 1. 查询理解：将自然语言输入和任务字段转换为结构化信息。
            updateStatus(taskNo, sessionId, TaskStatus.QUERY_UNDERSTANDING, null, 0);
            QueryUnderstandingResult queryUnderstanding = queryUnderstandingService.understand(taskNo, sessionId, command);
            AgentStepEntity queryStep = startStep(taskNo, "QUERY-UNDERSTANDING", "QueryUnderstandingService",
                    null, JsonSupport.toJson(command));
            finishStep(queryStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(queryUnderstanding), null);

            // 2. 意图分类：从启用的意图叶子节点中选择最匹配的业务意图。
            updateStatus(taskNo, sessionId, TaskStatus.INTENT_CLASSIFYING, null, 0);
            List<IntentLeafView> activeLeaves = intentTreeService.activeLeaves();
            IntentClassificationResult intentClassification = intentClassifier.classify(taskNo, sessionId,
                    queryUnderstanding.originalQuery(), queryUnderstanding, activeLeaves, List.of(), "");
            AgentStepEntity intentStep = startStep(taskNo, "INTENT-CLASSIFICATION", "IntentClassifier",
                    null, JsonSupport.toJson(Map.of("leafCount", activeLeaves.size(), "query", queryUnderstanding)));
            finishStep(intentStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(intentClassification), null);

            // 3. 歧义检查：判断输入是否足够执行。如果需要澄清，则暂停任务等待用户补充。
            updateStatus(taskNo, sessionId, TaskStatus.AMBIGUITY_CHECKING, null, 0);
            AmbiguityDecision ambiguity = ambiguityResolver.resolve(taskNo, sessionId, command,
                    queryUnderstanding, intentClassification);
            AgentStepEntity ambiguityStep = startStep(taskNo, "AMBIGUITY-CHECK", "IntentAmbiguityResolver",
                    null, JsonSupport.toJson(intentClassification));
            finishStep(ambiguityStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(ambiguity), null);
            if (ambiguity.needsClarification()) {
                updateStatus(taskNo, sessionId, TaskStatus.NEEDS_CLARIFICATION,
                        ambiguity.clarificationQuestion(), 0);
                return;
            }

            // 4. 上下文解析：加载 Memory Center 规则、历史经验和工作记忆。
            updateStatus(taskNo, sessionId, TaskStatus.CONTEXT_RESOLVING, null, 0);
            MemoryCenterContext memoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                    command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                    List.of(command.taskType(), command.projectKey()), 6,
                    taskNo, "MemoryCenter", "CONTEXT_RESOLVING"));
            memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "CONTEXT_RESOLVING",
                    "Loaded resident rules and recalled historical bug episodes.",
                    Map.of("coreRules", memoryContext.coreRules().size(),
                            "recalledEpisodes", memoryContext.recalledEpisodes().size()));

            // 5. 解析意图、时间范围和项目上下文，为后续工具调用准备已知事实。
            IntentLeafView selectedIntent = activeLeaves.stream()
                    .filter(leaf -> leaf.nodeCode().equals(intentClassification.selectedIntentCode()))
                    .findFirst()
                    .orElse(null);
            ResolvedTimeRange timeRange = timeRangeResolver.resolve(command, queryUnderstanding, selectedIntent);
            ProjectContext projectContext = projectContextResolver.resolve(command, queryUnderstanding);

            // 6. 如果开启 MCP ReAct 模式，则使用 ReAct 调查流程并提前返回。
            if (properties.getMcpReact().isEnabled()) {
                runMcpReactFlow(taskNo, sessionId, command, queryUnderstanding, intentClassification,
                        selectedIntent, timeRange, projectContext, memoryContext);
                return;
            }

            // 7. 传统模式：规划工具调用。
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

            // 证据编号从当前任务已有证据数量之后继续递增，避免重复编号。
            int nextEvidenceIndex = evidenceRepository.findByTaskNoOrderByIdAsc(taskNo).size() + 1;
            int maxRounds = Math.max(1, properties.getMaxRounds());

            for (int round = 1; round <= maxRounds; round++) {
                if (plans.isEmpty()) {
                    throw new BusinessException("NO_TOOL_PLAN", "No executable tool plan was generated from the task input.");
                }

                executedPlans.addAll(plans);
                updateStatus(taskNo, sessionId, TaskStatus.EXECUTING, null, round);

                // 并行执行本轮工具计划，收集工具结果、证据和 Agent 发现。
                ParallelAgentExecutionReport executionReport = parallelAgentExecutionService.collectEvidence(
                        taskNo, sessionId, command, plans);
                allResults.addAll(executionReport.toolResults());
                findings.addAll(executionReport.findings());

                // 持久化证据并写入 RAG 索引。
                updateStatus(taskNo, sessionId, TaskStatus.EVIDENCE_BUILDING, null, round);
                List<EvidenceItem> persistedEvidence = persistEvidence(taskNo, command.projectKey(), executionReport.evidence(), nextEvidenceIndex);
                nextEvidenceIndex += persistedEvidence.size();
                toolEvidenceRagIndexer.index(taskNo, command.projectKey(), persistedEvidence);
                evidence = mergeEvidence(evidence, persistedEvidence);

                // 更新共享工作记忆，供后续 Agent 使用当前任务进度。
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

                // 根据最新证据症状重新召回相关历史经验。
                finalMemoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                        command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                        evidenceSymptoms(evidence), 6,
                        taskNo, "MemoryCenter", "EVIDENCE_RECALL_R" + round));

                // 评审当前证据是否足够，决定最终输出或继续补充证据。
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

                // 证据不足时根据评审意见进行补充规划，进入下一轮。
                updateStatus(taskNo, sessionId, TaskStatus.REPLANNING, null, round);
                plans = plannerAgent.replan(command, finalMemoryContext, critique, executedPlans, allResults, evidence, round + 1);
                AgentStepEntity replanStep = startStep(taskNo, "REPLAN-R" + (round + 1), "PlannerAgent", null, JsonSupport.toJson(critique));
                finishStep(replanStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(plannerAgent.describe(command, plans, finalMemoryContext)), null);
                memoryCenterService.appendAgentNote(sessionId, "PlannerAgent", "REPLANNING",
                        "Generated supplemental tool plan after critique.",
                        Map.of("nextRound", round + 1, "toolCount", plans.size()));
            }

            // 生成最终报告并完成任务。
            updateStatus(taskNo, sessionId, TaskStatus.FINALIZING, null, Math.max(1, getTask(taskNo).currentRound));
            String report = finalReportAgent.generate(taskNo, sessionId, command.taskType(), evidence, critique, finalMemoryContext);
            updateStatus(taskNo, sessionId, TaskStatus.COMPLETED, report, getTask(taskNo).currentRound);

            // 将完成任务沉淀为可复用的情节记忆。
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

    /**
     * 执行 MCP ReAct 调查流程。
     *
     * <p>该流程用于替代传统的 Planner + 并行工具执行链路。MCPReActAgent 会基于调查上下文进行多轮
     * “思考、工具调用、观察、反思”，直到达到证据充分条件或达到配置限制。</p>
     *
     * @param taskNo               任务编号
     * @param sessionId            会话编号
     * @param command              任务创建命令
     * @param queryUnderstanding   查询理解结果
     * @param intentClassification 意图分类结果
     * @param selectedIntent       选中的意图叶子节点，可为空
     * @param timeRange            解析后的时间范围
     * @param projectContext       项目上下文
     * @param memoryContext        Memory Center 上下文
     */
    private void runMcpReactFlow(String taskNo,
                                 String sessionId,
                                 CreateAgentTaskCommand command,
                                 QueryUnderstandingResult queryUnderstanding,
                                 IntentClassificationResult intentClassification,
                                 IntentLeafView selectedIntent,
                                 ResolvedTimeRange timeRange,
                                 ProjectContext projectContext,
                                 MemoryCenterContext memoryContext) {
        updateStatus(taskNo, sessionId, TaskStatus.MCP_REACT_EXECUTING, null, 1);

        // 构建 ReAct 初始已知事实和缺失事实列表。
        Map<String, Object> knownFacts = initialKnownFacts(command, queryUnderstanding, projectContext, timeRange);
        List<String> missingFacts = new ArrayList<>(projectContext.missingFields());
        if (selectedIntent != null) {
            missingFacts.addAll(selectedIntent.requiredEvidenceTypes());
        }

        // 执行多轮 MCP ReAct 调查。
        MCPReActResult reactResult = mcpReActAgent.execute(new InvestigationContext(
                taskNo,
                sessionId,
                command,
                queryUnderstanding,
                intentClassification,
                selectedIntent,
                timeRange,
                projectContext,
                memoryContext,
                knownFacts,
                missingFacts,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        ));

        // 记录 ReAct 执行统计，写入共享工作记忆。
        memoryCenterService.updateWorkingContext(sessionId, Map.of(
                "mcpReactRounds", reactResult.rounds(),
                "mcpReactStoppedBySufficiency", reactResult.stoppedBySufficiency(),
                "mcpReactEvidenceCount", reactResult.evidence().size(),
                "mcpReactRejectedToolCalls", reactResult.rejectedToolCalls().size()
        ));
        memoryCenterService.appendAgentNote(sessionId, "MCPReActAgent", "MCP_REACT_EXECUTING",
                "Completed MCP ReAct tool planning, guarded execution and observation reflection.",
                Map.of("rounds", reactResult.rounds(),
                        "toolCalls", reactResult.executedPlans().size(),
                        "evidenceCount", reactResult.evidence().size(),
                        "rejectedToolCalls", reactResult.rejectedToolCalls().size()));

        // 持久化 ReAct 收集到的证据，并写入 RAG 索引。
        updateStatus(taskNo, sessionId, TaskStatus.EVIDENCE_BUILDING, null, Math.max(1, reactResult.rounds()));
        int nextEvidenceIndex = evidenceRepository.findByTaskNoOrderByIdAsc(taskNo).size() + 1;
        List<EvidenceItem> evidence = persistEvidence(taskNo, command.projectKey(), reactResult.evidence(), nextEvidenceIndex);
        toolEvidenceRagIndexer.index(taskNo, command.projectKey(), evidence);

        // 基于最新证据重新召回历史经验。
        MemoryCenterContext finalMemoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                evidenceSymptoms(evidence), 6,
                taskNo, "MemoryCenter", "EVIDENCE_RECALL_REACT"));

        // 对 ReAct 结果进行统一评审。
        updateStatus(taskNo, sessionId, TaskStatus.CRITIQUING, null, Math.max(1, reactResult.rounds()));
        Map<String, Object> critique = critiqueAgent.critique(reactResult.executedPlans(), reactResult.toolResults(),
                evidence, finalMemoryContext, reactResult.findings());
        AgentStepEntity critiqueStep = startStep(taskNo, "CRITIQUE-REACT", "CritiqueAgent", null,
                JsonSupport.toJson(reactResult.reflection()));
        finishStep(critiqueStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(critique), null);

        // 无证据时不生成最终报告，避免输出无证据结论。
        if (evidence.isEmpty()) {
            throw new BusinessException("EVIDENCE_INCOMPLETE", JsonSupport.toJson(critique));
        }

        // 生成最终报告并完成任务。
        updateStatus(taskNo, sessionId, TaskStatus.FINALIZING, null, Math.max(1, reactResult.rounds()));
        String report = finalReportAgent.generate(taskNo, sessionId, command.taskType(), evidence, critique, finalMemoryContext);
        updateStatus(taskNo, sessionId, TaskStatus.COMPLETED, report, Math.max(1, reactResult.rounds()));

        // 将完成任务沉淀到 Memory Center。
        MemoryEpisodeMatch consolidated = memoryCenterService.consolidateBugExperience(
                command.projectKey(), command.taskType(), taskNo, evidence, critique, report);
        memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "EPISODE_CONSOLIDATION",
                "Consolidated completed task into episodic memory.",
                Map.of("episodeId", consolidated == null || consolidated.episodeId() == null ? "N/A" : consolidated.episodeId()));
    }

    /**
     * 创建并保存一个执行步骤记录。
     *
     * @param taskNo    任务编号
     * @param stepId    步骤编号
     * @param agentName Agent 名称
     * @param toolName  工具名称，可为空
     * @param inputJson 步骤输入 JSON
     * @return 已保存的步骤实体
     */
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

    /**
     * 构造 MCP ReAct 初始已知事实。
     *
     * <p>该方法会整合项目上下文、用户命令、查询理解结果和时间范围，形成结构化 knownFacts。
     * ReAct Agent 可以基于这些事实判断哪些信息已经明确，哪些信息仍需通过工具调用补充。</p>
     *
     * @param command            任务创建命令
     * @param queryUnderstanding 查询理解结果
     * @param projectContext     项目上下文
     * @param timeRange          解析后的时间范围
     * @return 初始已知事实 Map
     */
    private Map<String, Object> initialKnownFacts(CreateAgentTaskCommand command,
                                                  QueryUnderstandingResult queryUnderstanding,
                                                  ProjectContext projectContext,
                                                  ResolvedTimeRange timeRange) {
        Map<String, Object> facts = new LinkedHashMap<>();
        if (projectContext != null) {
            facts.putAll(projectContext.asKnownFacts());
        }
        putIfPresent(facts, "taskType", command.taskType());
        putIfPresent(facts, "projectKey", command.projectKey());
        putIfPresent(facts, "serviceName", command.serviceName());
        putIfPresent(facts, "gitlabMrUrl", command.gitlabMrUrl());
        putIfPresent(facts, "jenkinsBuildUrl", command.jenkinsBuildUrl());
        putIfPresent(facts, "sonarqubeProjectKey", command.sonarqubeProjectKey());
        putIfPresent(facts, "jiraIssueKey", command.jiraIssueKey());
        putIfPresent(facts, "traceId", queryUnderstanding.traceId());
        putIfPresent(facts, "commitSha", queryUnderstanding.commitSha());
        putIfPresent(facts, "branch", queryUnderstanding.branch());
        putIfPresent(facts, "errorMessage", queryUnderstanding.errorMessage());
        if (timeRange != null) {
            facts.put("startTime", timeRange.startTime().toString());
            facts.put("endTime", timeRange.endTime().toString());
            facts.put("timeRangeHours", timeRange.hours());
        }
        return facts;
    }

    /**
     * 向 facts 中写入非空字段。
     *
     * @param facts 目标事实 Map
     * @param key   字段名
     * @param value 字段值
     */
    private void putIfPresent(Map<String, Object> facts, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            facts.put(key, value);
        }
    }

    /**
     * 完成并更新步骤记录。
     *
     * @param step          步骤实体
     * @param status        步骤状态
     * @param outputSummary 输出摘要
     * @param errorMessage  错误信息，可为空
     */
    private void finishStep(AgentStepEntity step, AgentStepStatus status, String outputSummary, String errorMessage) {
        step.status = status.name();
        step.outputSummary = outputSummary;
        step.errorMessage = errorMessage;
        step.finishedAt = LocalDateTime.now();
        stepRepository.save(step);
    }

    /**
     * 持久化证据列表。
     *
     * <p>该方法会为每条证据生成 taskNo 作用域内的 evidenceNo，
     * 并将 evidenceNo、originalRawRef 等信息追加到 metadata 中。</p>
     *
     * @param taskNo     任务编号
     * @param projectKey 项目标识
     * @param evidence   待持久化的证据列表
     * @param startIndex 证据起始序号
     * @return 带 evidenceNo 和更新后 metadata 的证据列表
     */
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

    /**
     * 更新任务和会话状态。
     *
     * @param taskNo    任务编号
     * @param sessionId 会话编号
     * @param status    新状态
     * @param report    最终报告或澄清问题，可为空
     * @param round     当前轮次
     */
    private void updateStatus(String taskNo, String sessionId, TaskStatus status, String report, int round) {
        AgentTaskEntity task = getTask(taskNo);
        task.status = status.name();
        task.currentRound = Math.max(0, round);
        if (report != null) {
            task.finalReport = report;
        }
        task.updatedAt = LocalDateTime.now();
        taskRepository.save(task);

        // 同步更新会话状态，保持 task/session 状态一致。
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
                session.status = status.name();
                session.updatedAt = LocalDateTime.now();
                sessionRepository.save(session);
            });
        }
    }

    /**
     * 生成任务编号。
     *
     * @return 格式为 TASK-yyyyMMddHHmmss-xxxxxxxx 的任务编号
     */
    private String nextTaskNo() {
        return "TASK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 构造用于 Memory Center 召回的查询文本。
     *
     * @param command 任务创建命令
     * @return 记忆召回查询文本
     */
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

    /**
     * 从证据中提取用于记忆召回的症状描述。
     *
     * @param evidence 当前累计证据
     * @return 症状描述列表
     */
    private List<String> evidenceSymptoms(List<EvidenceItem> evidence) {
        return evidence.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(8)
                .map(item -> "%s %s %s".formatted(item.title(), item.summary(), item.matchReason()))
                .toList();
    }

    /**
     * 合并当前证据和新增证据。
     *
     * <p>相同 evidenceKey 的证据只保留分数更高的一条，最终按分数倒序返回。</p>
     *
     * @param current 当前累计证据
     * @param next    新增证据
     * @return 合并并排序后的证据列表
     */
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

    /**
     * 生成证据去重键。
     *
     * @param item 证据项
     * @return 由来源、URL、路径、行范围和标题组成的去重键
     */
    private String evidenceKey(EvidenceItem item) {
        return "%s|%s|%s|%s|%s".formatted(
                value(item.sourceSystem()),
                value(item.sourceUrl()),
                value(item.filePath()),
                value(item.lineRange()),
                value(item.title())
        );
    }

    /**
     * 将 null 字符串转换为空字符串。
     *
     * @param value 原始值
     * @return 非 null 字符串
     */
    private String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * Spring Bean 销毁前关闭执行器。
     *
     * <p>用于避免应用关闭时仍有后台任务占用资源。</p>
     */
    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }
}
