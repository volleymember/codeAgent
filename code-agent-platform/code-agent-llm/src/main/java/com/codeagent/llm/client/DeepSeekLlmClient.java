package com.codeagent.llm.client;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.llm.audit.LlmCallRecorder;
import com.codeagent.llm.config.LlmProperties;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.LlmResponse;
import com.codeagent.llm.model.ManagedPrompt;
import com.codeagent.llm.model.TokenBudget;
import com.codeagent.llm.router.ModelRouter;
import com.codeagent.llm.token.TokenCostGuard;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekLlmClient implements LlmClient {
    private final LlmProperties properties;
    private final ModelRouter modelRouter;
    private final LlmCallRecorder recorder;
    private final TokenCostGuard tokenCostGuard;
    private final RestClient restClient;

    public DeepSeekLlmClient(LlmProperties properties,
                             ModelRouter modelRouter,
                             LlmCallRecorder recorder,
                             TokenCostGuard tokenCostGuard) {
        this.properties = properties;
        this.modelRouter = modelRouter;
        this.recorder = recorder;
        this.tokenCostGuard = tokenCostGuard;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        TokenBudget budget = request.tokenBudget() == null
                ? new TokenBudget(properties.getMaxInputTokens(), properties.getMaxOutputTokens(), properties.getMaxEvidenceTokens())
                : request.tokenBudget();
        ManagedPrompt prompt = tokenCostGuard.fit(request, budget);
        String model = modelRouter.selectModel(request.taskType(), budget);
        long start = System.nanoTime();
        try {
            if (!properties.hasApiKey()) {
                throw new BusinessException("LLM_API_KEY_MISSING", "DEEPSEEK_API_KEY is not configured.");
            }
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", prompt.systemPrompt()),
                            Map.of("role", "user", "content", prompt.userPrompt())
                    ),
                    "temperature", request.temperature() == null ? 0.2 : request.temperature(),
                    "max_tokens", request.maxTokens() == null
                            ? Math.min(properties.getMaxOutputTokens(), budget.maxOutputTokens())
                            : Math.min(request.maxTokens(), budget.maxOutputTokens())
            );

            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            String content = response.path("choices").path(0).path("message").path("content").asText();
            long inputTokens = response.path("usage").path("prompt_tokens").asLong(0);
            long outputTokens = response.path("usage").path("completion_tokens").asLong(0);
            String requestId = response.path("id").asText();
            recorder.record(request.taskNo(), request.sessionId(), model, request.taskType().name(),
                    inputTokens, outputTokens, prompt.estimatedInputTokens(), prompt.maxInputTokens(),
                    prompt.budgetPolicy(), latencyMs, "SUCCESS", null);
            return new LlmResponse(requestId, model, content, inputTokens, outputTokens, latencyMs);
        } catch (Exception e) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            recorder.record(request.taskNo(), request.sessionId(), model, request.taskType().name(),
                    0, 0, prompt.estimatedInputTokens(), prompt.maxInputTokens(),
                    prompt.budgetPolicy(), latencyMs, "FAILED", e.getMessage());
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("LLM_CALL_FAILED", "DeepSeek LLM call failed.", e);
        }
    }
}
