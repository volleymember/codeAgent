package com.codeagent.core.parallel;

import java.util.List;

public interface AgentWorkQueue {
    AgentWorkBatch enqueue(String taskNo, List<AgentWorkItem> items);
}
