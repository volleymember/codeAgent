package com.codeagent.mcp.tool;

import com.codeagent.mcp.model.ToolCallRequest;

@FunctionalInterface
public interface ToolHandler {
    ToolExecutionPayload handle(ToolCallRequest request);
}
