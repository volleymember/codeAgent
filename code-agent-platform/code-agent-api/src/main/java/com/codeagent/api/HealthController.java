package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.llm.config.LlmProperties;
import com.codeagent.mcp.config.IntegrationsProperties;
import com.codeagent.rag.config.EmbeddingProperties;
import com.codeagent.storage.config.MinioProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MinioProperties minioProperties;
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;
    private final IntegrationsProperties integrationsProperties;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
                            MinioProperties minioProperties, LlmProperties llmProperties,
                            EmbeddingProperties embeddingProperties, IntegrationsProperties integrationsProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.minioProperties = minioProperties;
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
        this.integrationsProperties = integrationsProperties;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("application", "UP");
        result.put("mysql", checkMySql());
        result.put("redis", checkRedis());
        result.put("minioConfigured", minioProperties.configured());
        result.put("deepseekConfigured", llmProperties.hasApiKey());
        result.put("embeddingProvider", embeddingProperties.getProvider());
        result.put("embeddingModel", embeddingProperties.getModel());
        result.put("embeddingDimensions", embeddingProperties.getDimensions());
        result.put("embeddingConfigured", embeddingProperties.hasApiKey());
        result.put("gitlabConfigured", integrationsProperties.getGitlab().configured());
        result.put("jenkinsConfigured", integrationsProperties.getJenkins().configured());
        result.put("sonarqubeConfigured", integrationsProperties.getSonarqube().configured());
        return ApiResponse.success(result);
    }

    private String checkMySql() {
        try {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }

    private String checkRedis() {
        try {
            String response = redisTemplate.getConnectionFactory().getConnection().ping();
            return response == null ? "UP" : response;
        } catch (Exception e) {
            return "DOWN: " + e.getMessage();
        }
    }
}
