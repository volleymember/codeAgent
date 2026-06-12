package com.codeagent.memory.model;

public record MemoryCenterStats(
        String projectKey,
        long coreRuleCount,
        long episodeCount,
        int maxCoreRules,
        int maxEpisodeRecall,
        double minRecallScore
) {
}
