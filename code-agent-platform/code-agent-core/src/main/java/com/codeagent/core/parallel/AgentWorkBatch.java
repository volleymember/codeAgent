package com.codeagent.core.parallel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class AgentWorkBatch {
    private final String taskNo;
    private final BlockingQueue<AgentWorkItem> queue;
    private final int totalCount;

    public AgentWorkBatch(String taskNo, BlockingQueue<AgentWorkItem> queue, int totalCount) {
        this.taskNo = taskNo;
        this.queue = queue;
        this.totalCount = Math.max(0, totalCount);
    }

    public String taskNo() {
        return taskNo;
    }

    public int totalCount() {
        return totalCount;
    }

    public AgentWorkItem poll(Duration timeout) throws InterruptedException {
        return queue.poll(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
    }

    public List<AgentWorkItem> drainAll() {
        List<AgentWorkItem> items = new ArrayList<>();
        queue.drainTo(items);
        return items;
    }
}
