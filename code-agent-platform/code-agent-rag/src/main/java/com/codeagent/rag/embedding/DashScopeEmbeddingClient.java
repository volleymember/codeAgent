package com.codeagent.rag.embedding;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.config.EmbeddingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DashScope 向量嵌入客户端。
 *
 * <p>该类负责调用 DashScope 兼容的 Embedding 接口，将输入文本转换为向量表示。
 * 生成的向量可用于 RAG 检索流程中的语义索引、相似度计算和召回排序。</p>
 *
 * <p>客户端通过 {@link EmbeddingProperties} 读取基础地址、模型名称、向量维度、
 * API Key 和超时时间等配置。</p>
 */
@Component
public class DashScopeEmbeddingClient implements EmbeddingClient {

    /**
     * Embedding 相关配置。
     *
     * <p>包括 baseUrl、model、dimensions、apiKey、timeoutMillis 等。</p>
     */
    private final EmbeddingProperties properties;

    /**
     * Spring RestClient 实例，用于发送 HTTP 请求。
     */
    private final RestClient restClient;

    /**
     * 创建 DashScope Embedding 客户端。
     *
     * <p>构造时会初始化 HTTP 请求工厂，并根据配置设置连接超时和读取超时。
     * 超时时间最小为 1000 毫秒，避免配置过小导致请求立即失败。</p>
     *
     * @param properties Embedding 配置对象
     */
    public DashScopeEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // 连接超时和读取超时均使用配置值，并限制最小值为 1000ms
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 将输入文本转换为向量。
     *
     * <p>该方法会向 DashScope Embedding 接口发送 POST 请求，
     * 请求体中包含模型名称、输入文本、目标维度和向量编码格式。</p>
     *
     * <p>返回结果会从响应中的 {@code data[0].embedding} 字段读取，
     * 并转换为 {@code List<Double>}。</p>
     *
     * @param input 待向量化的文本
     * @return 文本对应的向量
     * @throws BusinessException 当 API Key 缺失、响应数据为空或向量为空时抛出
     */
    @Override
    public List<Double> embed(String input) {
        // 调用外部 Embedding 服务前必须确保 API Key 已配置
        if (!properties.hasApiKey()) {
            throw new BusinessException("EMBEDDING_API_KEY_MISSING", "DASHSCOPE_API_KEY is not configured.");
        }

        JsonNode response = restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(Map.of(
                        "model", properties.getModel(),
                        "input", input,
                        "dimensions", properties.getDimensions(),
                        "encoding_format", "float"
                ))
                .retrieve()
                .body(JsonNode.class);

        // 校验响应结构，确保 data 字段存在且至少包含一个结果
        if (response == null || response.path("data").isMissingNode() || response.path("data").isEmpty()) {
            throw new BusinessException("EMBEDDING_RESPONSE_INVALID", "Embedding provider returned empty data.");
        }

        List<Double> vector = new ArrayList<>();

        // DashScope 兼容响应通常将向量放在 data[0].embedding 中
        for (JsonNode value : response.path("data").path(0).path("embedding")) {
            vector.add(value.asDouble());
        }

        // 防御性校验：避免将空向量写入向量库或参与相似度计算
        if (vector.isEmpty()) {
            throw new BusinessException("EMBEDDING_VECTOR_EMPTY", "Embedding provider returned an empty vector.");
        }

        return vector;
    }
}