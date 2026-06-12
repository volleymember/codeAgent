package com.codeagent.api;

import com.codeagent.common.api.ApiResponse;
import com.codeagent.mcp.model.ToolCallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.model.ToolRouteCandidate;
import com.codeagent.mcp.model.ToolRouteRequest;
import com.codeagent.mcp.router.McpRouter;
import com.codeagent.storage.entity.ToolCallRecordEntity;
import com.codeagent.storage.repository.ToolCallRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final McpRouter router;
    private final ToolCallRecordRepository repository;

    public ToolController(McpRouter router, ToolCallRecordRepository repository) {
        this.router = router;
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<ToolDefinition>> list() {
        return ApiResponse.success(router.listTools());
    }

    @PostMapping("/call")
    public ApiResponse<ToolCallResult> call(@RequestBody ToolCallRequest request) {
        return ApiResponse.success(router.call(request));
    }

    @PostMapping("/route")
    public ApiResponse<List<ToolRouteCandidate>> route(@RequestBody ToolRouteRequest request) {
        return ApiResponse.success(router.routeTools(request));
    }

    @GetMapping("/calls/{taskNo}")
    public ApiResponse<List<ToolCallRecordEntity>> calls(@PathVariable String taskNo) {
        return ApiResponse.success(repository.findByTaskNoOrderByIdAsc(taskNo));
    }
}
