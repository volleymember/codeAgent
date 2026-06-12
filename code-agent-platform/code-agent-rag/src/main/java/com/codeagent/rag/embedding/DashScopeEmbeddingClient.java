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

@Component
public class DashScopeEmbeddingClient implements EmbeddingClient {
    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public DashScopeEmbeddingClient(EmbeddingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public List<Double> embed(String input) {
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
        if (response == null || response.path("data").isMissingNode() || response.path("data").isEmpty()) {
            throw new BusinessException("EMBEDDING_RESPONSE_INVALID", "Embedding provider returned empty data.");
        }
        List<Double> vector = new ArrayList<>();
        for (JsonNode value : response.path("data").path(0).path("embedding")) {
            vector.add(value.asDouble());
        }
        if (vector.isEmpty()) {
            throw new BusinessException("EMBEDDING_VECTOR_EMPTY", "Embedding provider returned an empty vector.");
        }
        return vector;
    }
}
