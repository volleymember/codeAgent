package com.codeagent.mcp.router;

import com.codeagent.common.enums.ToolCallStatus;
import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.security.SensitiveDataMasker;
import com.codeagent.mcp.model.ToolCallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.model.ToolRouteCandidate;
import com.codeagent.mcp.model.ToolRouteRequest;
import com.codeagent.mcp.tool.ToolExecutor;
import com.codeagent.storage.entity.ToolCallRecordEntity;
import com.codeagent.storage.repository.ToolCallRecordRepository;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MCP 工具路由器。
 *
 * <p>作为 MCP 工具体系的统一入口，负责工具列表查询、工具路由、工具调用和调用审计。
 * PlannerAgent 通常会先调用 {@link #routeTools(ToolRouteRequest)} 选择候选工具，
 * 执行阶段再通过 {@link #call(ToolCallRequest)} 实际调用指定工具。</p>
 *
 * <p>该类本身不实现具体工具逻辑，具体工具由 {@link ToolExecutor} 负责执行；
 * 工具注册和查找由 {@link ToolRegistry} 负责；
 * 工具选择和排序由 {@link ToolSelectionService} 负责。</p>
 */
@Service
public class McpRouter {
    private final ToolRegistry registry;
    private final ToolCallRecordRepository toolCallRecordRepository;
    private final ToolSelectionService toolSelectionService;

    /**
     * 工具调用线程池。
     *
     * <p>每次工具调用会在虚拟线程中执行，避免阻塞当前调用线程。
     * 适合 I/O 密集型工具，例如访问 GitLab、Jenkins、SonarQube、Jira 等外部系统。</p>
     */
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 创建 MCP 工具路由器。
     *
     * @param registry                 工具注册表，用于查询工具定义和工具执行器
     * @param toolCallRecordRepository 工具调用记录仓储，用于保存审计日志
     * @param toolSelectionService     工具选择服务，用于根据任务请求对工具进行打分和排序
     */
    public McpRouter(ToolRegistry registry,
                     ToolCallRecordRepository toolCallRecordRepository,
                     ToolSelectionService toolSelectionService) {
        this.registry = registry;
        this.toolCallRecordRepository = toolCallRecordRepository;
        this.toolSelectionService = toolSelectionService;
    }

    /**
     * 查询当前已注册的全部工具定义。
     *
     * <p>该方法通常用于管理端展示、调试接口，或 PlannerAgent 规划前查看可用工具。</p>
     *
     * @return 已注册工具定义列表
     */
    public List<ToolDefinition> listTools() {
        return registry.list();
    }

    /**
     * 根据任务请求选择候选工具。
     *
     * <p>该方法会将当前工具注册表中的全部工具定义交给 {@link ToolSelectionService}，
     * 由选择服务根据任务类型、项目、用户目标、可用输入、工具标签和成本等因素进行评分，
     * 最终返回按相关性排序的候选工具列表。</p>
     *
     * @param request 工具路由请求
     * @return 工具路由候选结果列表
     */
    public List<ToolRouteCandidate> routeTools(ToolRouteRequest request) {
        return toolSelectionService.route(request, registry.list());
    }

    /**
     * 调用指定 MCP 工具。
     *
     * <p>执行流程如下：</p>
     * <ol>
     *     <li>根据工具名称从 {@link ToolRegistry} 中获取对应的 {@link ToolExecutor}。</li>
     *     <li>在虚拟线程中异步执行工具调用。</li>
     *     <li>根据工具定义中的超时时间限制执行时长。</li>
     *     <li>调用成功后记录审计日志并返回工具结果。</li>
     *     <li>调用失败或超时后构造失败结果，记录审计日志并返回。</li>
     * </ol>
     *
     * <p>该方法会兜底捕获异常，因此通常不会把工具执行异常继续向上抛出，
     * 而是转换为状态为 {@code FAILED} 的 {@link ToolCallResult}。</p>
     *
     * @param request 工具调用请求，包含工具名称、任务编号和输入参数等信息
     * @return 工具调用结果
     */
    public ToolCallResult call(ToolCallRequest request) {
        ToolExecutor executor = registry.get(request.toolName());
        long start = System.nanoTime();
        try {
            ToolCallResult result = CompletableFuture
                    .supplyAsync(() -> executor.execute(request), executorService)
                    .orTimeout(executor.definition().timeoutMs(), TimeUnit.MILLISECONDS)
                    .join();
            audit(request, result, null);
            return result;
        } catch (Exception e) {
            long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
            String message = rootMessage(e);
            ToolCallResult result = new ToolCallResult(request.toolName(), ToolCallStatus.FAILED.name(),
                    null, null, List.of(), message, latencyMs);
            audit(request, result, message);
            return result;
        }
    }

    /**
     * 记录工具调用审计日志。
     *
     * <p>审计信息包括任务编号、工具名称、脱敏后的输入、脱敏后的输出摘要、原始输出引用、
     * 调用状态、耗时、错误信息、token 统计、压缩比例、沙箱标记和创建时间。</p>
     *
     * <p>输入参数和输出摘要会经过 {@link SensitiveDataMasker} 脱敏后再保存，
     * 避免 Token、密码、密钥等敏感数据进入审计表。</p>
     *
     * @param request      工具调用请求
     * @param result       工具调用结果
     * @param errorMessage 错误信息；成功调用时通常为空
     */
    private void audit(ToolCallRequest request, ToolCallResult result, String errorMessage) {
        ToolCallRecordEntity record = new ToolCallRecordEntity();
        record.taskNo = request.taskNo() == null ? "UNKNOWN" : request.taskNo();
        record.toolName = request.toolName();
        record.inputJson = SensitiveDataMasker.mask(JsonSupport.toJson(request.input()));
        record.outputSummary = result.summary() == null ? null : SensitiveDataMasker.mask(result.summary());
        record.rawOutputUri = result.rawRef();
        record.status = result.status();
        record.latencyMs = result.latencyMs();
        record.errorMessage = errorMessage;
        record.rawTokens = (long) result.rawTokens();
        record.contextTokens = (long) result.contextTokens();
        record.compressionRatio = result.compressionRatio();
        record.sandboxed = result.sandboxed();
        record.createdAt = LocalDateTime.now();
        toolCallRecordRepository.save(record);
    }

    /**
     * 提取异常链中的根因错误信息。
     *
     * <p>当异常被 {@link CompletableFuture} 或运行时框架包装时，
     * 最外层异常通常不是最有价值的信息。该方法会沿着 cause 链向下查找，
     * 返回最底层异常的 message；如果 message 为空，则返回异常类名。</p>
     *
     * @param e 捕获到的异常
     * @return 根因错误信息
     */
    private String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    /**
     * 销毁 Bean 时关闭工具调用线程池。
     *
     * <p>Spring 容器关闭时会调用该方法，及时中断仍在执行的工具调用，
     * 释放虚拟线程执行器相关资源。</p>
     */
    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }
}