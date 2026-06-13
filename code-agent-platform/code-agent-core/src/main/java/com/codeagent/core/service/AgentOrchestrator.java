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

/**
 * Agent 任务编排服务。
 *
 * <p>该类负责完整调度一次 Agent 任务的生命周期，包括：</p>
 * <ul>
 *     <li>创建任务和会话</li>
 *     <li>加载 Memory Center 上下文</li>
 *     <li>调用 PlannerAgent 生成工具计划</li>
 *     <li>并行执行工具并收集证据</li>
 *     <li>持久化证据并写入 RAG 索引</li>
 *     <li>调用 CritiqueAgent 评估证据是否充分</li>
 *     <li>必要时重新规划并进入下一轮执行</li>
 *     <li>生成最终报告并沉淀经验记忆</li>
 * </ul>
 *
 * <p>任务创建后会异步提交到虚拟线程池中执行，因此创建接口会立即返回任务记录。</p>
 */
@Service
public class AgentOrchestrator {

    /**
     * Agent 任务仓储。
     */
    private final AgentTaskRepository taskRepository;

    /**
     * Agent 会话仓储。
     */
    private final AgentSessionRepository sessionRepository;

    /**
     * Agent 执行步骤仓储。
     */
    private final AgentStepRepository stepRepository;

    /**
     * 证据记录仓储。
     */
    private final EvidenceRecordRepository evidenceRepository;

    /**
     * 规划 Agent，负责根据任务输入和记忆上下文生成工具执行计划。
     */
    private final PlannerAgent plannerAgent;

    /**
     * 评审 Agent，负责判断当前证据是否足够支撑最终结论。
     */
    private final CritiqueAgent critiqueAgent;

    /**
     * 最终报告 Agent，负责基于证据和评审结果生成最终报告。
     */
    private final FinalReportAgent finalReportAgent;

    /**
     * 并行 Agent 执行服务，负责并发调用多个工具采集证据。
     */
    private final ParallelAgentExecutionService parallelAgentExecutionService;

    /**
     * Memory Center 服务，负责上下文构建、工作记忆更新和经验沉淀。
     */
    private final MemoryCenterService memoryCenterService;

    /**
     * 工具证据 RAG 索引器，负责将采集到的证据写入检索索引。
     */
    private final ToolEvidenceRagIndexer toolEvidenceRagIndexer;

    /**
     * Agent 配置，例如最大轮次等。
     */
    private final AgentProperties properties;

    /**
     * 异步任务执行器。
     *
     * <p>使用虚拟线程执行每个 Agent 任务，适合包含大量 I/O 调用的工具执行流程。</p>
     */
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建 Agent 任务编排服务。
     */
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

        // 保存完整用户输入，便于后续审计、回放和排查问题
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

        // 异步执行任务，避免阻塞创建任务接口
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
     * <p>该方法按照“上下文加载 - 规划 - 执行 - 证据构建 - 评审 - 重规划 - 总结”的流程推进任务。
     * 每一轮执行后都会由 CritiqueAgent 判断是否可以进入最终报告阶段。</p>
     *
     * @param taskNo    任务编号
     * @param sessionId 会话编号
     * @param command   任务创建命令
     */
    private void runTask(String taskNo, String sessionId, CreateAgentTaskCommand command) {
        try {
            updateStatus(taskNo, sessionId, TaskStatus.CONTEXT_RESOLVING, null, 0);

            // 构建 Memory Center 上下文：包含常驻规则、历史经验和当前会话工作记忆
            MemoryCenterContext memoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                    command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                    List.of(command.taskType(), command.projectKey()), 6,
                    taskNo, "MemoryCenter", "CONTEXT_RESOLVING"));

            memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "CONTEXT_RESOLVING",
                    "Loaded resident rules and recalled historical bug episodes.",
                    Map.of("coreRules", memoryContext.coreRules().size(),
                            "recalledEpisodes", memoryContext.recalledEpisodes().size()));

            updateStatus(taskNo, sessionId, TaskStatus.PLANNING, null, 0);

            // 首轮规划：根据用户输入和记忆上下文生成工具计划
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

            // 证据编号从当前任务已有证据数量之后继续递增，避免重复编号
            int nextEvidenceIndex = evidenceRepository.findByTaskNoOrderByIdAsc(taskNo).size() + 1;
            int maxRounds = Math.max(1, properties.getMaxRounds());

            for (int round = 1; round <= maxRounds; round++) {
                if (plans.isEmpty()) {
                    throw new BusinessException("NO_TOOL_PLAN", "No executable tool plan was generated from the task input.");
                }

                executedPlans.addAll(plans);

                updateStatus(taskNo, sessionId, TaskStatus.EXECUTING, null, round);

                // 并行执行本轮工具计划，收集工具结果、证据和 Agent 发现
                ParallelAgentExecutionReport executionReport = parallelAgentExecutionService.collectEvidence(
                        taskNo, sessionId, command, plans);

                allResults.addAll(executionReport.toolResults());
                findings.addAll(executionReport.findings());

                updateStatus(taskNo, sessionId, TaskStatus.EVIDENCE_BUILDING, null, round);

                // 持久化本轮证据，并将其写入 RAG 索引
                List<EvidenceItem> persistedEvidence = persistEvidence(taskNo, command.projectKey(), executionReport.evidence(), nextEvidenceIndex);
                nextEvidenceIndex += persistedEvidence.size();
                toolEvidenceRagIndexer.index(taskNo, command.projectKey(), persistedEvidence);

                // 合并多轮证据，并按分数保留更优结果
                evidence = mergeEvidence(evidence, persistedEvidence);

                // 更新共享工作记忆，便于后续 Agent 使用当前任务状态
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

                // 根据最新证据症状重新召回相关历史经验
                finalMemoryContext = memoryCenterService.buildContext(new MemoryRecallRequest(
                        command.projectKey(), command.taskType(), memoryQuery(command), sessionId,
                        evidenceSymptoms(evidence), 6,
                        taskNo, "MemoryCenter", "EVIDENCE_RECALL_R" + round));

                updateStatus(taskNo, sessionId, TaskStatus.CRITIQUING, null, round);

                // 评审当前证据是否足够，决定最终输出或继续补充证据
                critique = critiqueAgent.critique(executedPlans, allResults, evidence, finalMemoryContext, findings);

                AgentStepEntity critiqueStep = startStep(taskNo, "CRITIQUE-R" + round, "CritiqueAgent", null, JsonSupport.toJson(critique));
                finishStep(critiqueStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(critique), null);

                memoryCenterService.appendAgentNote(sessionId, "CritiqueAgent", "CRITIQUING",
                        "Evaluated evidence completeness with shared memory context.",
                        Map.of("round", round, "decision", critique.get("decision"), "confidence", critique.get("confidence")));

                // 评审认为证据足够时结束循环，进入最终报告生成
                if ("FINALIZE".equals(critique.get("decision"))) {
                    break;
                }

                // 达到最大轮次仍无法完成时，任务失败，避免生成无证据结论
                if (round >= maxRounds) {
                    throw new BusinessException("EVIDENCE_INCOMPLETE", JsonSupport.toJson(critique));
                }

                updateStatus(taskNo, sessionId, TaskStatus.REPLANNING, null, round);

                // 根据评审意见进行补充规划，进入下一轮证据采集
                plans = plannerAgent.replan(command, finalMemoryContext, critique, executedPlans, allResults, evidence, round + 1);

                AgentStepEntity replanStep = startStep(taskNo, "REPLAN-R" + (round + 1), "PlannerAgent", null, JsonSupport.toJson(critique));
                finishStep(replanStep, AgentStepStatus.SUCCESS, JsonSupport.toJson(plannerAgent.describe(command, plans, finalMemoryContext)), null);

                memoryCenterService.appendAgentNote(sessionId, "PlannerAgent", "REPLANNING",
                        "Generated supplemental tool plan after critique.",
                        Map.of("nextRound", round + 1, "toolCount", plans.size()));
            }

            updateStatus(taskNo, sessionId, TaskStatus.FINALIZING, null, Math.max(1, getTask(taskNo).currentRound));

            // 基于最终证据、评审结果和记忆上下文生成报告
            String report = finalReportAgent.generate(taskNo, sessionId, command.taskType(), evidence, critique, finalMemoryContext);

            updateStatus(taskNo, sessionId, TaskStatus.COMPLETED, report, getTask(taskNo).currentRound);

            // 将完成任务沉淀为可复用的经验记忆
            MemoryEpisodeMatch consolidated = memoryCenterService.consolidateBugExperience(
                    command.projectKey(), command.taskType(), taskNo, evidence, critique, report);

            memoryCenterService.appendAgentNote(sessionId, "MemoryCenter", "EPISODE_CONSOLIDATION",
                    "Consolidated completed task into episodic memory.",
                    Map.of("episodeId", consolidated == null || consolidated.episodeId() == null ? "N/A" : consolidated.episodeId()));
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();

            // 任何异常都会将任务标记为失败，并明确不生成无证据结论
            updateStatus(taskNo, sessionId, TaskStatus.FAILED, "任务失败，未生成无证据结论。\n\n原因: " + message,
                    Math.max(0, getTask(taskNo).currentRound == null ? 0 : getTask(taskNo).currentRound));
        }
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

            // 复制原始 metadata，并补充持久化后的证据编号
            Map<String, Object> metadata = new LinkedHashMap<>(item.metadata());
            metadata.put("evidenceNo", entity.evidenceNo);
            metadata.put("originalRawRef", item.rawRef());

            entity.metadata = JsonSupport.toJson(metadata);
            entity.createdAt = LocalDateTime.now();
            evidenceRepository.save(entity);

            // 返回的 EvidenceItem 使用持久化后的 evidenceNo 作为 rawRef，便于报告引用
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
     * @param report    最终报告，可为空
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

        // 同步更新会话状态，保持 task/session 状态一致
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
     * <p>编号格式为 TASK-时间戳-UUID前8位。</p>
     *
     * @return 新任务编号
     */
    private String nextTaskNo() {
        return "TASK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 构造用于 Memory Center 召回的查询文本。
     *
     * <p>查询文本会拼接任务类型、项目、MR、构建、SonarQube 和 Jira 等关键信息，
     * 用于召回相似历史任务或规则。</p>
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
     * <p>优先选取得分最高的前 8 条证据，将标题、摘要和匹配原因拼接成简短描述。</p>
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
     * <p>合并时使用 sourceSystem、sourceUrl、filePath、lineRange 和 title 组成唯一键。
     * 如果新旧证据键相同，则保留分数更高的一条。</p>
     *
     * @param current 当前累计证据
     * @param next    新增证据
     * @return 合并并按得分倒序排列后的证据列表
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
     * @return 由来源、路径、行范围和标题组成的去重键
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