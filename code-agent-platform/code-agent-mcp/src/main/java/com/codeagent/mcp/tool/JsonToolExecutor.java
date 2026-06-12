package com.codeagent.mcp.tool;

import com.codeagent.common.enums.ToolCallStatus;
import com.codeagent.common.exception.BusinessException;
import com.codeagent.mcp.model.ToolCallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.sandbox.DataSandboxService;
import com.codeagent.mcp.sandbox.SandboxedToolPayload;
import com.codeagent.storage.raw.RawOutputStore;

import java.time.Duration;

public class JsonToolExecutor implements ToolExecutor {
    private final ToolDefinition definition;
    private final RawOutputStore rawOutputStore;
    private final DataSandboxService dataSandboxService;
    private final ToolHandler handler;

    public JsonToolExecutor(ToolDefinition definition,
                            RawOutputStore rawOutputStore,
                            DataSandboxService dataSandboxService,
                            ToolHandler handler) {
        this.definition = definition;
        this.rawOutputStore = rawOutputStore;
        this.dataSandboxService = dataSandboxService;
        this.handler = handler;
    }

    @Override
    public ToolDefinition definition() {
        return definition;
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        validate(request);
        long start = System.nanoTime();
        ToolExecutionPayload payload = handler.handle(request);
        String rawRef = rawOutputStore.saveJson(request.taskNo(), definition.name(), payload.rawPayload());
        SandboxedToolPayload sandboxed = dataSandboxService.sandbox(definition, payload, rawRef);
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new ToolCallResult(definition.name(), ToolCallStatus.SUCCESS.name(),
                sandboxed.summary(), rawRef, sandboxed.evidence(), null, latencyMs,
                sandboxed.rawTokens(), sandboxed.contextTokens(), sandboxed.compressionRatio(), true);
    }

    private void validate(ToolCallRequest request) {
        for (String required : definition.requiredInputs()) {
            String value = request.stringInput(required);
            if (value == null || value.isBlank()) {
                throw new BusinessException("TOOL_INPUT_INVALID",
                        "Missing required input `%s` for tool `%s`.".formatted(required, definition.name()));
            }
        }
    }
}
