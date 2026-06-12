package com.codeagent.core.parallel;

import com.codeagent.common.enums.AgentStepStatus;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.storage.entity.AgentStepEntity;
import com.codeagent.storage.repository.AgentStepRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class ParallelAgentExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ParallelAgentExecutionService.class);

    private final AgentWorkPlanner workPlanner;
    private final AgentWorkQueue workQueue;
    private final AgentWorkRunner workRunner;
    private final AgentResultAggregator resultAggregator;
    private final AgentStepRepository stepRepository;
    private final AgentProperties properties;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public ParallelAgentExecutionService(AgentWorkPlanner workPlanner,
                                         AgentWorkQueue workQueue,
                                         AgentWorkRunner workRunner,
                                         AgentResultAggregator resultAggregator,
                                         AgentStepRepository stepRepository,
                                         AgentProperties properties) {
        this.workPlanner = workPlanner;
        this.workQueue = workQueue;
        this.workRunner = workRunner;
        this.resultAggregator = resultAggregator;
        this.stepRepository = stepRepository;
        this.properties = properties;
    }

    public ParallelAgentExecutionReport collectEvidence(String taskNo,
                                                        String sessionId,
                                                        CreateAgentTaskCommand command,
                                                        List<ToolPlan> toolPlans) {
        long startedAt = System.nanoTime();
        List<AgentWorkItem> plannedItems = workPlanner.plan(taskNo, sessionId, command, toolPlans);
        AgentWorkBatch batch = workQueue.enqueue(taskNo, plannedItems);
        List<AgentWorkItem> items = drain(batch);
        if (items.isEmpty()) {
            return resultAggregator.aggregate(taskNo, batch.totalCount(), 0, List.of());
        }
        Semaphore concurrency = new Semaphore(Math.max(1, properties.getMaxParallelAgents()));
        List<CompletableFuture<AgentExecutionResult>> futures = new ArrayList<>();
        for (AgentWorkItem item : items) {
            CompletableFuture<AgentExecutionResult> future = CompletableFuture
                    .supplyAsync(() -> executeWithPermit(concurrency, item), executorService)
                    .orTimeout(properties.getParallelTaskTimeoutMs(), TimeUnit.MILLISECONDS)
                    .exceptionally(error -> timeoutResult(item, error));
            futures.add(future);
        }
        List<AgentExecutionResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        ParallelAgentExecutionReport report = resultAggregator.aggregate(taskNo, batch.totalCount(), latencyMs, results);
        log.info("Parallel evidence collection finished taskNo={} submitted={} success={} failed={} evidence={} latencyMs={}",
                taskNo, report.submittedCount(), report.successfulCount(), report.failedCount(),
                report.evidence().size(), report.latencyMs());
        return report;
    }

    private List<AgentWorkItem> drain(AgentWorkBatch batch) {
        List<AgentWorkItem> items = new ArrayList<>();
        try {
            AgentWorkItem item;
            while ((item = batch.poll(Duration.ofMillis(properties.getQueuePollTimeoutMs()))) != null) {
                items.add(item);
                if (items.size() >= batch.totalCount()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return items;
    }

    private AgentExecutionResult executeWithPermit(Semaphore concurrency, AgentWorkItem item) {
        boolean acquired = false;
        AgentStepEntity step = startStep(item);
        try {
            acquired = concurrency.tryAcquire(Math.max(1000, item.timeoutMs()), TimeUnit.MILLISECONDS);
            if (!acquired) {
                AgentExecutionResult result = timeoutResult(item,
                        new IllegalStateException("parallel agent concurrency permit timeout"));
                finishStep(step, AgentStepStatus.FAILED, JsonSupport.toJson(result.auditSummary()), result.errorMessage());
                return result;
            }
            AgentExecutionResult result = workRunner.run(item);
            finishStep(step, result.success() ? AgentStepStatus.SUCCESS : AgentStepStatus.FAILED,
                    JsonSupport.toJson(result.auditSummary()), result.errorMessage());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            AgentExecutionResult result = timeoutResult(item, e);
            finishStep(step, AgentStepStatus.FAILED, JsonSupport.toJson(result.auditSummary()), result.errorMessage());
            return result;
        } catch (Exception e) {
            AgentExecutionResult result = timeoutResult(item, e);
            finishStep(step, AgentStepStatus.FAILED, JsonSupport.toJson(result.auditSummary()), result.errorMessage());
            return result;
        } finally {
            if (acquired) {
                concurrency.release();
            }
        }
    }

    private AgentExecutionResult timeoutResult(AgentWorkItem item, Throwable error) {
        String message = rootMessage(error);
        log.warn("Parallel agent work timeout/failure taskNo={} stepId={} toolName={} error={}",
                item.taskNo(), item.stepId(), item.toolName(), message);
        var result = new com.codeagent.mcp.model.ToolCallResult(
                item.toolName(), "FAILED", null, null, List.of(), message, item.timeoutMs());
        return new AgentExecutionResult(item, result, item.maxAttempts(), false, item.required(), item.timeoutMs(), message);
    }

    private AgentStepEntity startStep(AgentWorkItem item) {
        AgentStepEntity step = new AgentStepEntity();
        step.taskNo = item.taskNo();
        step.stepId = item.stepId();
        step.agentName = item.agentName();
        step.toolName = item.toolName();
        step.status = AgentStepStatus.RUNNING.name();
        step.inputJson = JsonSupport.toJson(item.auditInput());
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

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }
}
