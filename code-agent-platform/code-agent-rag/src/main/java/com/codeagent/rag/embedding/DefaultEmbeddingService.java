package com.codeagent.rag.embedding;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.config.EmbeddingProperties;
import com.codeagent.rag.model.EvidenceChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 默认 Embedding 服务实现。
 *
 * <p>该服务负责将文本或 EvidenceChunk 转换为向量表示。
 * 它在底层 EmbeddingClient 之外增加了输入校验、重试、失败计数和简易熔断能力，
 * 用于提升外部向量服务调用的稳定性。</p>
 *
 * <p>核心能力包括：</p>
 * <ul>
 *     <li>校验待向量化文本是否为空</li>
 *     <li>调用 EmbeddingClient 生成向量</li>
 *     <li>对可重试异常进行多次重试</li>
 *     <li>连续失败达到阈值后打开熔断</li>
 *     <li>支持将 EvidenceChunk 转换为适合向量化的文本</li>
 * </ul>
 */
@Slf4j
@Service
public class DefaultEmbeddingService implements EmbeddingService {

    /**
     * 底层 Embedding 客户端，负责实际调用向量模型服务。
     */
    private final EmbeddingClient embeddingClient;

    /**
     * EvidenceChunk 文本构建器。
     *
     * <p>用于将 chunk 的标题、路径、内容、元数据等信息组合成适合向量化的文本。</p>
     */
    private final EmbeddingTextBuilder textBuilder;

    /**
     * Embedding 配置。
     *
     * <p>包含最大重试次数、重试退避时间、熔断阈值和熔断打开时长等参数。</p>
     */
    private final EmbeddingProperties properties;

    /**
     * 连续失败次数。
     *
     * <p>调用成功后会被重置为 0；重试耗尽后会递增。
     * 当连续失败次数达到熔断阈值时，将打开熔断。</p>
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * 熔断打开时间戳。
     *
     * <p>值为 0 表示熔断关闭；非 0 表示熔断打开的时间点，单位为毫秒。</p>
     */
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);

    /**
     * 创建默认 Embedding 服务。
     *
     * @param embeddingClient 底层向量客户端
     * @param textBuilder     EvidenceChunk 文本构建器
     * @param properties      Embedding 配置
     */
    public DefaultEmbeddingService(EmbeddingClient embeddingClient,
                                   EmbeddingTextBuilder textBuilder,
                                   EmbeddingProperties properties) {
        this.embeddingClient = embeddingClient;
        this.textBuilder = textBuilder;
        this.properties = properties;
    }

    /**
     * 将普通文本转换为向量。
     *
     * <p>该方法会先校验文本内容，然后检查熔断状态。
     * 如果熔断关闭，则按配置的最大重试次数调用底层 EmbeddingClient。
     * 调用成功后会清空连续失败计数并关闭熔断。</p>
     *
     * <p>当所有重试均失败后，会累加连续失败次数；
     * 如果连续失败次数达到熔断阈值，则记录熔断打开时间。</p>
     *
     * @param text 待向量化的文本
     * @return 文本对应的向量
     * @throws BusinessException 当文本为空、熔断打开、调用失败或重试被中断时抛出
     */
    @Override
    public List<Double> embed(String text) {
        // 待向量化文本不能为空
        if (text == null || text.isBlank()) {
            throw new BusinessException("EMBEDDING_TEXT_EMPTY", "Embedding text must not be empty.");
        }

        // 若熔断处于打开状态，则直接拒绝调用，避免持续请求不可用的外部服务
        ensureCircuitClosed();

        int attempts = Math.max(1, properties.getMaxAttempts());
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                List<Double> vector = embeddingClient.embed(text);

                // 成功后重置失败计数和熔断状态
                consecutiveFailures.set(0);
                circuitOpenedAt.set(0);

                return vector;
            } catch (BusinessException e) {
                // 配置缺失、输入为空等错误不应重试，直接抛出
                if (nonRetryable(e)) {
                    throw e;
                }

                lastError = e;
                log.warn("Embedding call failed attempt={}/{} code={} message={}",
                        attempt, attempts, e.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                // 其他运行时异常通常可能是网络、超时、服务端错误等，允许进入重试流程
                lastError = e;
                log.warn("Embedding call failed attempt={}/{} error={}", attempt, attempts, e.toString());
            }

            // 非最后一次失败时，根据配置进行退避等待
            sleepBeforeRetry(attempt, attempts);
        }

        // 所有重试失败后，更新连续失败次数
        int failures = consecutiveFailures.incrementAndGet();

        // 连续失败达到阈值时打开熔断
        if (failures >= Math.max(1, properties.getCircuitFailureThreshold())) {
            circuitOpenedAt.set(System.currentTimeMillis());
        }

        log.error("Embedding call exhausted retries failures={}", failures, lastError);

        throw new BusinessException("EMBEDDING_CALL_FAILED", "Embedding provider call failed after retries.", lastError);
    }

    /**
     * 将 EvidenceChunk 转换为向量。
     *
     * <p>该方法会先通过 EmbeddingTextBuilder 构建适合向量化的文本，
     * 再复用 {@link #embed(String)} 完成向量生成。</p>
     *
     * @param chunk 待向量化的 EvidenceChunk
     * @return chunk 对应的向量
     */
    @Override
    public List<Double> embed(EvidenceChunk chunk) {
        return embed(textBuilder.build(chunk));
    }

    /**
     * 检查熔断器是否允许当前调用继续执行。
     *
     * <p>当熔断未打开时直接返回；
     * 当熔断已打开且尚未达到配置的打开时长时，直接抛出业务异常；
     * 当熔断打开时间已超过配置值时，会尝试关闭熔断，允许后续请求再次尝试调用。</p>
     *
     * @throws BusinessException 当熔断仍处于打开状态时抛出
     */
    private void ensureCircuitClosed() {
        long openedAt = circuitOpenedAt.get();

        // 0 表示熔断关闭
        if (openedAt == 0) {
            return;
        }

        long openMillis = Math.max(1000, properties.getCircuitOpenMillis());

        // 熔断打开时间未到，拒绝本次请求
        if (System.currentTimeMillis() - openedAt < openMillis) {
            throw new BusinessException("EMBEDDING_CIRCUIT_OPEN", "Embedding provider circuit is open after repeated failures.");
        }

        // 熔断时间已过，尝试关闭熔断，允许下一次调用进入重试流程
        circuitOpenedAt.compareAndSet(openedAt, 0);
    }

    /**
     * 判断指定 BusinessException 是否不可重试。
     *
     * <p>配置缺失、输入为空等错误通常与外部服务临时状态无关，
     * 重试无法恢复，因此应直接抛出。</p>
     *
     * @param e 业务异常
     * @return 如果该异常不应重试则返回 true，否则返回 false
     */
    private boolean nonRetryable(BusinessException e) {
        return "EMBEDDING_API_KEY_MISSING".equals(e.getCode())
                || "EMBEDDING_TEXT_EMPTY".equals(e.getCode());
    }

    /**
     * 在下一次重试前进行退避等待。
     *
     * <p>等待时间为 {@code retryBackoffMillis * attempt}，
     * 即失败次数越多，等待时间越长。</p>
     *
     * @param attempt  当前尝试次数，从 1 开始
     * @param attempts 最大尝试次数
     * @throws BusinessException 当等待过程中线程被中断时抛出
     */
    private void sleepBeforeRetry(int attempt, int attempts) {
        // 最后一次失败后不再等待
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