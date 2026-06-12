package com.codeagent.memory;

import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.CoreMemoryItem;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.repository.MemoryCoreRepository;
import com.codeagent.storage.repository.MemoryEpisodeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryCenterServiceTest {
    @Test
    void buildsLayeredContextForSharedAgentMemory() {
        CoreMemoryService coreMemoryService = mock(CoreMemoryService.class);
        WorkingMemoryService workingMemoryService = mock(WorkingMemoryService.class);
        EpisodicMemoryService episodicMemoryService = mock(EpisodicMemoryService.class);
        MemoryRecallAuditService memoryRecallAuditService = mock(MemoryRecallAuditService.class);
        MemoryCoreRepository coreRepository = mock(MemoryCoreRepository.class);
        MemoryEpisodeRepository episodeRepository = mock(MemoryEpisodeRepository.class);
        MemoryProperties properties = new MemoryProperties();
        MemoryCenterService service = new MemoryCenterService(coreMemoryService, workingMemoryService,
                episodicMemoryService, memoryRecallAuditService, properties, coreRepository, episodeRepository);
        when(coreMemoryService.loadResidentRules("payment-service")).thenReturn(List.of(
                new CoreMemoryItem(1L, "payment-service", "CODING_RULE",
                        "All payment changes must keep idempotency.", List.of("payment"), 100, "doc://rule")
        ));
        when(episodicMemoryService.recall(any(MemoryRecallRequest.class))).thenReturn(List.of(
                new MemoryEpisodeMatch("EP-1", "payment-service", List.of("timeout", "retry"),
                        "Retry policy was missing timeout guard.",
                        "Add timeout guard and regression test.",
                        "task:TASK-1", "agent-task://TASK-1", "verified", 0.82,
                        "matched retry timeout")
        ));
        when(workingMemoryService.merge("SESSION-1", Map.of(
                "projectKey", "payment-service",
                "taskType", "CI_FAILURE_ANALYSIS",
                "query", "retry timeout",
                "lastMemoryResolveAt", "ignored",
                "residentCoreRuleCount", 1,
                "recalledEpisodeIds", List.of("EP-1")
        ))).thenReturn(Map.of("projectKey", "payment-service"));
        when(workingMemoryService.merge(any(), any())).thenReturn(Map.of("projectKey", "payment-service"));

        MemoryCenterContext context = service.buildContext(new MemoryRecallRequest(
                "payment-service", "CI_FAILURE_ANALYSIS", "retry timeout", "SESSION-1", List.of("timeout"), 3));

        assertThat(context.coreRules()).hasSize(1);
        assertThat(context.recalledEpisodes()).hasSize(1);
        assertThat(context.workingContext()).containsEntry("projectKey", "payment-service");
    }
}
