package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.storage.entity.IntegrationConfigEntity;
import com.codeagent.storage.repository.IntegrationConfigRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/integrations")
public class IntegrationController {
    private final IntegrationConfigRepository repository;

    public IntegrationController(IntegrationConfigRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<IntegrationConfigEntity>> list() {
        return ApiResponse.success(repository.findAll());
    }

    @PostMapping("/{platform}")
    public ApiResponse<IntegrationConfigEntity> save(@PathVariable String platform, @RequestBody IntegrationConfigRequest request) {
        IntegrationConfigEntity entity = new IntegrationConfigEntity();
        entity.platform = platform.toLowerCase();
        entity.projectKey = request.projectKey();
        entity.baseUrl = request.baseUrl();
        entity.authType = request.authType() == null ? "ENV_SECRET_REF" : request.authType();
        entity.secretRef = request.secretRef();
        entity.enabled = request.enabled() == null || request.enabled();
        entity.connectionStatus = "UNVERIFIED";
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;
        return ApiResponse.success(repository.save(entity));
    }

    public record IntegrationConfigRequest(
            String projectKey,
            @NotBlank String baseUrl,
            String authType,
            @NotBlank String secretRef,
            Boolean enabled
    ) {
    }
}
