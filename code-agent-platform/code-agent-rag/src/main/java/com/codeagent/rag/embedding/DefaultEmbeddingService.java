package com.codeagent.rag.embedding;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.config.EmbeddingProperties;
import com.codeagent.rag.model.EvidenceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class DefaultEmbeddingService implements EmbeddingService {
    private final EmbeddingClient embeddingClient;
    private final EmbeddingTextBuilder textBuilder;
    private final EmbeddingProperties properties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);

    public DefaultEmbeddingService(EmbeddingClient embeddingClient,
                                   EmbeddingTextBuilder textBuilder,
                                   EmbeddingProperties properties) {
        this.embeddingClient = embeddingClient;
        this.textBuilder = textBuilder;
        this.properties = properties;
    }

    @Override
    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException("EMBEDDING_TEXT_EMPTY", "Embedding text must not be empty.");
        }
        ensureCircuitClosed();
        int attempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                List<Double> vector = embeddingClient.embed(text);
                consecutiveFailures.set(0);
                circuitOpenedAt.set(0);
                return vector;
            } catch (BusinessException e) {
                if (nonRetryable(e)) {
                    throw e;
                }
                lastError = e;
                log.warn("Embedding call failed attempt={}/{} code={} message={}",
                        attempt, attempts, e.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("Embedding call failed attempt={}/{} error={}", attempt, attempts, e.toString());
            }
            sleepBeforeRetry(attempt, attempts);
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, properties.getCircuitFailureThreshold())) {
            circuitOpenedAt.set(System.currentTimeMillis());
        }
        log.error("Embedding call exhausted retries failures={}", failures, lastError);
        throw new BusinessException("EMBEDDING_CALL_FAILED", "Embedding provider call failed after retries.", lastError);
    }

    @Override
    public List<Double> embed(EvidenceChunk chunk) {
        return embed(textBuilder.build(chunk));
    }

    private void ensureCircuitClosed() {
        long openedAt = circuitOpenedAt.get();
        if (openedAt == 0) {
            return;
        }
        long openMillis = Math.max(1000, properties.getCircuitOpenMillis());
        if (System.currentTimeMillis() - openedAt < openMillis) {
            throw new BusinessException("EMBEDDING_CIRCUIT_OPEN", "Embedding provider circuit is open after repeated failures.");
        }
        circuitOpenedAt.compareAndSet(openedAt, 0);
    }

    private boolean nonRetryable(BusinessException e) {
        return "EMBEDDING_API_KEY_MISSING".equals(e.getCode())
                || "EMBEDDING_TEXT_EMPTY".equals(e.getCode());
    }

    private void sleepBeforeRetry(int attempt, int attempts) {
        if (attempt >= attempts) {
            return;
        }
        try {
            Thread.sleep(Math.max(0, properties.getRetryBackoffMillis()) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("EMBEDDING_RETRY_INTERRUPTED", "Embedding retry interrupted.", e);
        }
    }
}
