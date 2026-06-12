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

@Service
public class AgentTaskRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(AgentTaskRecoveryService.class);
    private static final Set<String> TERMINAL = Set.of(TaskStatus.COMPLETED.name(), TaskStatus.FAILED.name());

    private final AgentTaskRepository taskRepository;
    private final AgentSessionRepository sessionRepository;

    public AgentTaskRecoveryService(AgentTaskRepository taskRepository, AgentSessionRepository sessionRepository) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedTasks() {
        List<String> runningStatuses = java.util.Arrays.stream(TaskStatus.values())
                .map(TaskStatus::name)
                .filter(status -> !TERMINAL.contains(status))
                .toList();
        List<AgentTaskEntity> interrupted = taskRepository.findByStatusIn(runningStatuses);
        for (AgentTaskEntity task : interrupted) {
            task.status = TaskStatus.FAILED.name();
            task.finalReport = "任务失败，未生成无证据结论。\n\n原因: 服务重启或执行器中断，进程内任务无法继续。请重新提交任务。";
            task.updatedAt = LocalDateTime.now();
            taskRepository.save(task);
            sessionRepository.findByTaskNo(task.taskNo).forEach(session -> {
                session.status = TaskStatus.FAILED.name();
                session.updatedAt = LocalDateTime.now();
                sessionRepository.save(session);
            });
        }
        if (!interrupted.isEmpty()) {
            log.warn("Marked interrupted agent tasks as FAILED count={}", interrupted.size());
        }
    }
}
