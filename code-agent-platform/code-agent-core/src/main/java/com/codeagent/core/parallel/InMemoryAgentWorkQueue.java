package com.codeagent.core.parallel;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class InMemoryAgentWorkQueue implements AgentWorkQueue {
    @Override
    public AgentWorkBatch enqueue(String taskNo, List<AgentWorkItem> items) {
        LinkedBlockingQueue<AgentWorkItem> queue = new LinkedBlockingQueue<>();
        if (items != null) {
            queue.addAll(items);
        }
        return new AgentWorkBatch(taskNo, queue, queue.size());
    }
}
