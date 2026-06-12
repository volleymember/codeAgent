package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.storage.repository.AgentTaskRepository;
import com.codeagent.storage.repository.EvidenceRecordRepository;
import com.codeagent.storage.repository.LlmCallRecordRepository;
import com.codeagent.storage.repository.ToolCallRecordRepository;
import com.codeagent.storage.entity.AgentTaskEntity;
import com.codeagent.storage.entity.LlmCallRecordEntity;
import com.codeagent.storage.entity.ToolCallRecordEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {
    private final AgentTaskRepository taskRepository;
    private final ToolCallRecordRepository toolCallRecordRepository;
    private final LlmCallRecordRepository llmCallRecordRepository;
    private final EvidenceRecordRepository evidenceRecordRepository;

    public MetricsController(AgentTaskRepository taskRepository, ToolCallRecordRepository toolCallRecordRepository,
                             LlmCallRecordRepository llmCallRecordRepository, EvidenceRecordRepository evidenceRecordRepository) {
        this.taskRepository = taskRepository;
        this.toolCallRecordRepository = toolCallRecordRepository;
        this.llmCallRecordRepository = llmCallRecordRepository;
        this.evidenceRecordRepository = evidenceRecordRepository;
    }

    @GetMapping({"/tasks", "/tools", "/llm", "/rag"})
    public ApiResponse<Map<String, Object>> metrics() {
        List<AgentTaskEntity> tasks = taskRepository.findAll();
        List<ToolCallRecordEntity> toolCalls = toolCallRecordRepository.findAll();
        List<LlmCallRecordEntity> llmCalls = llmCallRecordRepository.findAll();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.putAll(Map.of(
                "tasks", taskRepository.count(),
                "toolCalls", toolCallRecordRepository.count(),
                "llmCalls", llmCallRecordRepository.count(),
                "evidence", evidenceRecordRepository.count()
        ));
        metrics.put("taskStatus", tasks.stream()
                .collect(Collectors.groupingBy(task -> task.status, LinkedHashMap::new, Collectors.counting())));
        long completed = tasks.stream().filter(task -> "COMPLETED".equals(task.status)).count();
        long failed = tasks.stream().filter(task -> "FAILED".equals(task.status)).count();
        metrics.put("taskSuccessRate", completed + failed == 0 ? 0.0 : (double) completed / (completed + failed));
        metrics.put("averageToolLatencyMs", average(toolCalls.stream()
                .map(call -> call.latencyMs)
                .toList()));
        metrics.put("toolFailureRate", toolCalls.isEmpty() ? 0.0 : (double) toolCalls.stream()
                .filter(call -> !"SUCCESS".equals(call.status))
                .count() / toolCalls.size());
        metrics.put("averageLlmInputTokens", average(llmCalls.stream()
                .map(call -> call.inputTokens)
                .toList()));
        metrics.put("averageLlmOutputTokens", average(llmCalls.stream()
                .map(call -> call.outputTokens)
                .toList()));
        metrics.put("groundedReportRate", groundedReportRate(tasks));
        return ApiResponse.success(metrics);
    }

    private double average(List<Long> values) {
        return values.stream()
                .filter(value -> value != null && value >= 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    private double groundedReportRate(List<AgentTaskEntity> tasks) {
        List<AgentTaskEntity> completed = tasks.stream()
                .filter(task -> "COMPLETED".equals(task.status))
                .toList();
        if (completed.isEmpty()) {
            return 0.0;
        }
        long grounded = completed.stream()
                .filter(task -> task.finalReport != null && evidenceRecordRepository.findByTaskNoOrderByIdAsc(task.taskNo).stream()
                        .anyMatch(evidence -> task.finalReport.contains(evidence.evidenceNo)))
                .count();
        return (double) grounded / completed.size();
    }
}
