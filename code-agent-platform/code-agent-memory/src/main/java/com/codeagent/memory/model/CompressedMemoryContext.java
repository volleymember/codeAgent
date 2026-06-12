package com.codeagent.memory.model;

import java.util.List;

public record CompressedMemoryContext(
        String policy,
        int originalCoreRuleCount,
        int includedCoreRuleCount,
        int originalEpisodeCount,
        int includedEpisodeCount,
        int originalAgentNoteCount,
        int includedAgentNoteCount,
        int estimatedTokens,
        int maxTokens,
        List<CompressedCoreRule> coreRules,
        List<CompressedEpisode> recalledBugEpisodes,
        List<CompressedAgentNote> agentNotes
) {
    public CompressedMemoryContext {
        coreRules = coreRules == null ? List.of() : List.copyOf(coreRules);
        recalledBugEpisodes = recalledBugEpisodes == null ? List.of() : List.copyOf(recalledBugEpisodes);
        agentNotes = agentNotes == null ? List.of() : List.copyOf(agentNotes);
    }
}
