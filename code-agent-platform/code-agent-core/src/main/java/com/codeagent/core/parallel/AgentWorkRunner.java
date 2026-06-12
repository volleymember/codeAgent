package com.codeagent.core.parallel;

import com.codeagent.common.enums.ToolCallStatus;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.mcp.model.ToolCallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.router.McpRouter;
import com.codeagent.rag.service.RagRetrievalService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class AgentWorkRunner {
    private static final Logger log = LoggerFactory.getLogger(AgentWorkRunner.class);

    private final McpRouter mcpRouter;
    private final RagRetrievalService ragRetrievalService;
    private final AgentProperties properties;
    private final ExecutorService timeoutExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AgentWorkRunner(McpRouter mcpRouter,
                           RagRetrievalService ragRetrievalService,
                           AgentProperties properties) {
        this.mcpRouter = mcpRouter;
        this.ragRetrievalService = ragRetrievalService;
        this.properties = properties;
    }

    public AgentExecutionResult run(AgentWorkItem item) {
        long startedAt = System.nanoTime();
        String lastError = null;
        ToolCallResult lastResult = null;
        int attempts = Math.max(1, item.maxAttempts());
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                ToolCallResult result = CompletableFuture
                        .supplyAsync(() -> executeOnce(item), timeoutExecutor)
                        .orTimeout(item.timeoutMs(), TimeUnit.MILLISECONDS)
                        .join();
                lastResult = result;
                if (ToolCallStatus.SUCCESS.name().equals(result.status())) {
                    return new AgentExecutionResult(item, result, attempt, true, false,
                            elapsedMs(startedAt), null);
                }
                lastError = result.errorMessage();
            } catch (Exception e) {
                lastError = rootMessage(e);
                log.warn("Parallel agent work failed taskNo={} stepId={} toolName={} attempt={}/{} error={}",
                        item.taskNo(), item.stepId(), item.toolName(), attempt, attempts, lastError);
            }
            if (attempt < attempts) {
                sleepBackoff(attempt);
            }
        }
        ToolCallResult failed = lastResult == null ? new ToolCallResult(
                item.toolName(), ToolCallStatus.FAILED.name(), null, null, List.of(), lastError, elapsedMs(startedAt))
                : lastResult;
        return new AgentExecutionResult(item, failed, attempts, false, item.required(), elapsedMs(startedAt), lastError);
    }

    private ToolCallResult executeOnce(AgentWorkItem item) {
        if (item.source() == AgentWorkSource.RAG_SEARCH) {
            long startedAt = System.nanoTime();
            var evidencePackage = ragRetrievalService.search(item.ragSearchRequest());
            long latencyMs = elapsedMs(startedAt);
            String summary = "%s returned %d evidence items for query `%s`.".formatted(
                    item.toolName(), evidencePackage.resultCount(), evidencePackage.query());
            return new ToolCallResult(item.toolName(), ToolCallStatus.SUCCESS.name(),
                    summary, null, evidencePackage.evidenceItems(), null, latencyMs);
        }
        return mcpRouter.call(new ToolCallRequest(item.taskNo(), item.toolName(), item.input()));
    }

    private void sleepBackoff(int attempt) {
        long backoffMs = Math.max(0, properties.getAgentRetryBackoffMs()) * attempt;
        if (backoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @PreDestroy
    void shutdown() {
        timeoutExecutor.shutdownNow();
    }
}
