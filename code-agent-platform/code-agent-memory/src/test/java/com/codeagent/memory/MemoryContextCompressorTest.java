package com.codeagent.memory;

import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.AgentMemoryNote;
import com.codeagent.memory.model.CompressedMemoryContext;
import com.codeagent.memory.model.CoreMemoryItem;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryContextCompressorTest {
    @Test
    void compressesMemoryContextWithinBudget() {
        MemoryProperties properties = new MemoryProperties();
        properties.setMaxContextTokens(160);
        properties.setMaxCoreRuleTokens(30);
        properties.setMaxEpisodeTokens(60);
        MemoryContextCompressor compressor = new MemoryContextCompressor(properties);
        String longText = "payment retry timeout idempotency ".repeat(80);
        MemoryCenterContext context = new MemoryCenterContext(
                "SESSION-1",
                "payment-service",
                List.of(
                        new CoreMemoryItem(1L, "payment-service", "RULE", longText, List.of("payment"), 100, "doc://rule"),
                        new CoreMemoryItem(2L, "payment-service", "RULE", longText, List.of("retry"), 90, "doc://rule2")
                ),
                Map.of("taskType", "CI_FAILURE_ANALYSIS"),
                List.of(new MemoryEpisodeMatch("EP-1", "payment-service", List.of(longText),
                        longText, longText, "task:TASK-1", "agent-task://TASK-1",
                        "verified", 0.91, longText)),
                List.of(new AgentMemoryNote("PlannerAgent", "PLANNING", longText, Map.of(), LocalDateTime.now()))
        );

        CompressedMemoryContext compressed = compressor.compress(context);

        assertThat(compressed.estimatedTokens()).isLessThanOrEqualTo(compressed.maxTokens() + 80);
        assertThat(compressed.policy()).isIn("PASS", "TOKEN_TRUNCATED");
        assertThat(compressed.includedCoreRuleCount()).isGreaterThanOrEqualTo(1);
        assertThat(compressed.coreRules().getFirst().content()).contains("[TRUNCATED]");
    }
}
