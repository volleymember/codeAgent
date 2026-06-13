package com.codeagent.core.intent.dto;

import jakarta.validation.constraints.NotBlank;

public record IntentTreeCreateRequest(
        @NotBlank String treeCode,
        @NotBlank String treeName,
        Integer version
) {
}
