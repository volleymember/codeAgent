package com.codeagent.mcp.observability;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.sandbox.DataSandboxService;
import com.codeagent.mcp.tool.JsonToolExecutor;
import com.codeagent.mcp.tool.ToolExecutionPayload;
import com.codeagent.mcp.tool.ToolExecutor;
import com.codeagent.storage.raw.RawOutputStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class ObservabilityToolConfig {
    @Bean
    ToolExecutor logSearchErrorsTool(RawOutputStore rawOutputStore,
                                     DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("log.search_errors", "Search configured log index for recent errors.",
                List.of("logIndex", "timeRange"), List.of("traceId", "exceptionName", "errorMessage"),
                List.of("log", "logs", "error", "exception", "time"), 1600, true, "discovery"),
                rawOutputStore, dataSandboxService, request -> {
            String logIndex = request.stringInput("logIndex");
            String summary = "Prepared log error search for index `%s` within configured time range.".formatted(logIndex);
            return new ToolExecutionPayload(Map.of("logIndex", logIndex, "timeRange", request.input().get("timeRange")),
                    summary, List.of(new EvidenceItem("log_error_search", "Log error search request", summary, 0.55,
                    "log://" + logIndex, null, Map.of("logIndex", logIndex))));
        });
    }

    @Bean
    ToolExecutor apmSearchTracesTool(RawOutputStore rawOutputStore,
                                     DataSandboxService dataSandboxService) {
        return new JsonToolExecutor(def("apm.search_traces", "Search configured APM service traces.",
                List.of("apmServiceName", "timeRange"), List.of("traceId", "spanName", "errorRate"),
                List.of("apm", "trace", "latency", "error", "time"), 1600, true, "discovery"),
                rawOutputStore, dataSandboxService, request -> {
            String serviceName = request.stringInput("apmServiceName");
            String summary = "Prepared APM trace search for service `%s` within configured time range.".formatted(serviceName);
            return new ToolExecutionPayload(Map.of("apmServiceName", serviceName, "timeRange", request.input().get("timeRange")),
                    summary, List.of(new EvidenceItem("apm_trace_search", "APM trace search request", summary, 0.55,
                    "apm://" + serviceName, null, Map.of("apmServiceName", serviceName))));
        });
    }

    private ToolDefinition def(String name,
                               String description,
                               List<String> requiredInputs,
                               List<String> outputFacts,
                               List<String> tags,
                               int estimatedOutputTokens,
                               boolean highCost,
                               String toolType) {
        return new ToolDefinition(name, "Observability", description, requiredInputs, 15000,
                tags, estimatedOutputTokens, highCost, List.of(), outputFacts, toolType, List.of(), highCost ? 8 : 3);
    }
}
