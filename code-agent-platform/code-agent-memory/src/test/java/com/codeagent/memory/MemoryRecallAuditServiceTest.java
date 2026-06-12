package com.codeagent.memory;

import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryRecallRecordEntity;
import com.codeagent.storage.repository.MemoryRecallRecordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRecallAuditServiceTest {
    @Test
    void recordsMemoryRecallMetadata() {
        MemoryRecallRecordRepository repository = mock(MemoryRecallRecordRepository.class);
        when(repository.save(any(MemoryRecallRecordEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MemoryRecallAuditService service = new MemoryRecallAuditService(repository);
        MemoryRecallRequest request = new MemoryRecallRequest(
                "payment-service", "CI_FAILURE_ANALYSIS", "retry timeout", "SESSION-1",
                List.of("timeout"), 3, "TASK-1", "MemoryCenter", "CONTEXT_RESOLVING");
        MemoryCenterContext context = new MemoryCenterContext("SESSION-1", "payment-service",
                List.of(), Map.of(), List.of(new MemoryEpisodeMatch("EP-1", "payment-service",
                List.of("timeout"), "root", "fix", "task:TASK-0", "agent-task://TASK-0",
                "verified", 0.88, "matched timeout")), List.of());

        MemoryRecallRecordEntity saved = service.record(request, context, 12);

        assertThat(saved.projectKey).isEqualTo("payment-service");
        assertThat(saved.taskNo).isEqualTo("TASK-1");
        assertThat(saved.episodeCount).isEqualTo(1);
        assertThat(saved.maxScore).isEqualTo(0.88);
        assertThat(saved.recalledEpisodeIds).contains("EP-1");
    }
}
