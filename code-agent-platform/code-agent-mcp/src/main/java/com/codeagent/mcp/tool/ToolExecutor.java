package com.codeagent.mcp.tool;

import com.codeagent.mcp.model.ToolCallRequest;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;

public interface ToolExecutor {
    ToolDefinition definition();

    ToolCallResult execute(ToolCallRequest request);
}
