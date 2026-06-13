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

/**
 * JSON 工具执行器。
 *
 * <p>该类是一个通用的 ToolExecutor 实现，用于执行返回 JSON 结构化结果的工具。
 * 它将工具调用流程拆分为：输入校验、业务处理、原始输出保存、沙箱压缩/清洗、
 * 结果封装几个步骤。</p>
 *
 * <p>实际工具业务逻辑由 {@link ToolHandler} 提供，因此该执行器可以复用于多个不同工具，
 * 只需传入不同的 ToolDefinition 和 ToolHandler 即可。</p>
 */
public class JsonToolExecutor implements ToolExecutor {

    /**
     * 工具定义信息。
     *
     * <p>包含工具名称、描述、输入参数、必填字段等元数据。</p>
     */
    private final ToolDefinition definition;

    /**
     * 原始输出存储服务。
     *
     * <p>用于保存工具返回的完整 JSON 原始结果，并生成 rawRef 供后续追溯。</p>
     */
    private final RawOutputStore rawOutputStore;

    /**
     * 数据沙箱服务。
     *
     * <p>用于对工具原始输出进行清洗、摘要、证据提取和 token 压缩，
     * 避免将过大的原始结果直接传递给后续 Agent。</p>
     */
    private final DataSandboxService dataSandboxService;

    /**
     * 工具处理器。
     *
     * <p>封装具体工具的执行逻辑，输入为 ToolCallRequest，输出为 ToolExecutionPayload。</p>
     */
    private final ToolHandler handler;

    /**
     * 创建 JSON 工具执行器。
     *
     * @param definition         工具定义
     * @param rawOutputStore     原始输出存储服务
     * @param dataSandboxService 数据沙箱服务
     * @param handler            工具处理器
     */
    public JsonToolExecutor(ToolDefinition definition,
                            RawOutputStore rawOutputStore,
                            DataSandboxService dataSandboxService,
                            ToolHandler handler) {
        this.definition = definition;
        this.rawOutputStore = rawOutputStore;
        this.dataSandboxService = dataSandboxService;
        this.handler = handler;
    }

    /**
     * 返回当前执行器对应的工具定义。
     *
     * @return 工具定义
     */
    @Override
    public ToolDefinition definition() {
        return definition;
    }

    /**
     * 执行工具调用。
     *
     * <p>执行流程如下：</p>
     * <ol>
     *     <li>校验请求中是否包含工具定义要求的必填参数</li>
     *     <li>调用 ToolHandler 执行具体工具逻辑</li>
     *     <li>保存工具原始 JSON 输出，生成 rawRef</li>
     *     <li>通过 DataSandboxService 对结果进行摘要、压缩和证据提取</li>
     *     <li>封装为 ToolCallResult 返回</li>
     * </ol>
     *
     * @param request 工具调用请求
     * @return 工具调用结果
     * @throws BusinessException 当必填输入缺失时抛出
     */
    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        validate(request);

        long start = System.nanoTime();

        // 执行具体工具业务逻辑，得到原始 payload
        ToolExecutionPayload payload = handler.handle(request);

        // 保存完整原始 JSON，后续可通过 rawRef 追溯原始工具输出
        String rawRef = rawOutputStore.saveJson(request.taskNo(), definition.name(), payload.rawPayload());

        // 对原始输出进行沙箱处理，提取摘要和证据，并统计 token 压缩信息
        SandboxedToolPayload sandboxed = dataSandboxService.sandbox(definition, payload, rawRef);

        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        return new ToolCallResult(definition.name(), ToolCallStatus.SUCCESS.name(),
                sandboxed.summary(), rawRef, sandboxed.evidence(), null, latencyMs,
                sandboxed.rawTokens(), sandboxed.contextTokens(), sandboxed.compressionRatio(), true);
    }

    /**
     * 校验工具调用请求中的必填参数。
     *
     * <p>必填参数来源于 ToolDefinition.requiredInputs。
     * 如果请求中缺少任一必填参数，或参数值为空白字符串，则抛出业务异常。</p>
     *
     * @param request 工具调用请求
     * @throws BusinessException 当必填参数缺失或为空时抛出
     */
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