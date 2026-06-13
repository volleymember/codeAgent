package com.codeagent.core.service;

import com.codeagent.common.enums.TaskStatus;
import com.codeagent.storage.entity.AgentTaskEntity;
import com.codeagent.storage.repository.AgentSessionRepository;
import com.codeagent.storage.repository.AgentTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Agent 任务恢复服务。
 *
 * <p>该服务用于在应用启动完成后检查历史任务状态，
 * 将上一次进程退出、服务重启或执行器中断时遗留的非终态任务标记为失败。</p>
 *
 * <p>由于 Agent 任务是在进程内异步执行的，服务重启后无法继续恢复原线程中的执行上下文。
 * 因此对于 CREATED、PLANNING、EXECUTING 等未完成状态的任务，会统一标记为 FAILED，
 * 并提示用户重新提交任务。</p>
 */
@Service
public class AgentTaskRecoveryService {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(AgentTaskRecoveryService.class);

    /**
     * 任务终态集合。
     *
     * <p>处于 COMPLETED 或 FAILED 状态的任务不需要恢复处理。</p>
     */
    private static final Set<String> TERMINAL = Set.of(TaskStatus.COMPLETED.name(), TaskStatus.FAILED.name());

    /**
     * Agent 任务仓储。
     */
    private final AgentTaskRepository taskRepository;

    /**
     * Agent 会话仓储。
     */
    private final AgentSessionRepository sessionRepository;

    /**
     * 创建 Agent 任务恢复服务。
     *
     * @param taskRepository    Agent 任务仓储
     * @param sessionRepository Agent 会话仓储
     */
    public AgentTaskRecoveryService(AgentTaskRepository taskRepository, AgentSessionRepository sessionRepository) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * 应用启动完成后，将上次遗留的运行中任务标记为失败。
     *
     * <p>该方法会在 Spring Boot 发布 {@link ApplicationReadyEvent} 后自动执行。
     * 它会枚举所有非终态 TaskStatus，并查询这些状态下的任务。
     * 对于查询到的任务，会同时更新任务状态和对应会话状态。</p>
     *
     * <p>这样可以避免服务重启后，旧任务一直停留在运行中状态，
     * 从而影响前端展示、任务查询或后续运维判断。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedTasks() {
        // 计算所有非终态状态，例如 CREATED、PLANNING、EXECUTING、FINALIZING 等
        List<String> runningStatuses = java.util.Arrays.stream(TaskStatus.values())
                .map(TaskStatus::name)
                .filter(status -> !TERMINAL.contains(status))
                .toList();

        // 查询所有上次服务退出时尚未完成的任务
        List<AgentTaskEntity> interrupted = taskRepository.findByStatusIn(runningStatuses);

        for (AgentTaskEntity task : interrupted) {
            // 任务无法在新进程中继续执行，因此统一标记为 FAILED
            task.status = TaskStatus.FAILED.name();
            task.finalReport = "任务失败，未生成无证据结论。\n\n原因: 服务重启或执行器中断，进程内任务无法继续。请重新提交任务。";
            task.updatedAt = LocalDateTime.now();
            taskRepository.save(task);

            // 同步更新该任务关联的所有会话状态
            sessionRepository.findByTaskNo(task.taskNo).forEach(session -> {
                session.status = TaskStatus.FAILED.name();
                session.updatedAt = LocalDateTime.now();
                sessionRepository.save(session);
            });
        }

        // 仅在确实存在被恢复处理的任务时输出告警日志
        if (!interrupted.isEmpty()) {
            log.warn("Marked interrupted agent tasks as FAILED count={}", interrupted.size());
        }
    }
}