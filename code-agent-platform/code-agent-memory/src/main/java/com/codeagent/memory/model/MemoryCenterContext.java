package com.codeagent.memory.model;

import java.util.List;
import java.util.Map;

public record MemoryCenterContext(
        String sessionId,
        String projectKey,
        List<CoreMemoryItem> coreRules,
        Map<String, Object> workingContext,
        List<MemoryEpisodeMatch> recalledEpisodes,
        List<AgentMemoryNote> agentNotes
) {
    public MemoryCenterContext {
        coreRules = coreRules == null ? List.of() : List.copyOf(coreRules);
        workingContext = workingContext == null ? Map.of() : Map.copyOf(workingContext);
        recalledEpisodes = recalledEpisodes == null ? List.of() : List.copyOf(recalledEpisodes);
        agentNotes = agentNotes == null ? List.of() : List.copyOf(agentNotes);
    }
}
