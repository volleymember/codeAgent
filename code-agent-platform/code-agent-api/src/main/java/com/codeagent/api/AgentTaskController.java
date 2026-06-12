package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.service.AgentOrchestrator;
import com.codeagent.storage.entity.AgentStepEntity;
import com.codeagent.storage.entity.AgentTaskEntity;
import com.codeagent.storage.entity.EvidenceRecordEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {
    private final AgentOrchestrator orchestrator;

    public AgentTaskController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ApiResponse<AgentTaskEntity> create(@Valid @RequestBody CreateAgentTaskCommand command) {
        return ApiResponse.success(orchestrator.createTask(command));
    }

    @GetMapping("/{taskNo}")
    public ApiResponse<AgentTaskEntity> get(@PathVariable String taskNo) {
        return ApiResponse.success(orchestrator.getTask(taskNo));
    }

    @GetMapping("/{taskNo}/steps")
    public ApiResponse<List<AgentStepEntity>> steps(@PathVariable String taskNo) {
        return ApiResponse.success(orchestrator.getSteps(taskNo));
    }

    @GetMapping("/{taskNo}/evidence")
    public ApiResponse<List<EvidenceRecordEntity>> evidence(@PathVariable String taskNo) {
        return ApiResponse.success(orchestrator.getEvidence(taskNo));
    }

    @GetMapping("/{taskNo}/report")
    public ApiResponse<Map<String, String>> report(@PathVariable String taskNo) {
        return ApiResponse.success(Map.of("taskNo", taskNo, "report", orchestrator.getTask(taskNo).finalReport == null ? "" : orchestrator.getTask(taskNo).finalReport));
    }
}
