package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.common.token.TokenEstimator;
import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.AgentMemoryNote;
import com.codeagent.memory.model.CompressedAgentNote;
import com.codeagent.memory.model.CompressedCoreRule;
import com.codeagent.memory.model.CompressedEpisode;
import com.codeagent.memory.model.CompressedMemoryContext;
import com.codeagent.memory.model.CoreMemoryItem;
import com.codeagent.memory.model.MemoryCenterContext;
import com.codeagent.memory.model.MemoryEpisodeMatch;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MemoryContextCompressor {
    private final MemoryProperties properties;

    public MemoryContextCompressor(MemoryProperties properties) {
        this.properties = properties;
    }

    public CompressedMemoryContext compress(MemoryCenterContext context) {
        if (context == null) {
            return new CompressedMemoryContext("EMPTY", 0, 0, 0, 0, 0, 0, 0,
                    Math.max(1, properties.getMaxContextTokens()), List.of(), List.of(), List.of());
        }
        int maxTokens = Math.max(1, properties.getMaxContextTokens());
        List<CompressedCoreRule> coreRules = new ArrayList<>();
        List<CompressedEpisode> episodes = new ArrayList<>();
        List<CompressedAgentNote> notes = new ArrayList<>();
        int estimated = 0;
        for (CoreMemoryItem rule : context.coreRules().stream()
                .sorted(Comparator.comparingInt(CoreMemoryItem::priority).reversed())
                .toList()) {
            CompressedCoreRule compressed = new CompressedCoreRule(
                    value(rule.type()),
                    truncate(rule.content(), properties.getMaxCoreRuleTokens()),
                    rule.tags(),
                    rule.priority(),
                    value(rule.sourceUri())
            );
            int tokens = estimate(compressed);
            if (estimated + tokens > maxTokens) {
                compressed = new CompressedCoreRule(
                        compressed.type(),
                        truncate(compressed.content(), Math.max(20, (maxTokens - estimated) / 2)),
                        compressed.tags(),
                        compressed.priority(),
                        compressed.sourceUri()
                );
                tokens = estimate(compressed);
                if (estimated + tokens > maxTokens) {
                    break;
                }
            }
            coreRules.add(compressed);
            estimated += tokens;
            if (estimated >= maxTokens) {
                break;
            }
        }
        for (MemoryEpisodeMatch episode : context.recalledEpisodes().stream()
                .sorted(Comparator.comparingDouble(MemoryEpisodeMatch::score).reversed())
                .toList()) {
            if (estimated >= maxTokens) {
                break;
            }
            CompressedEpisode compressed = new CompressedEpisode(
                    value(episode.episodeId()),
                    episode.symptoms().stream().limit(6).map(symptom -> truncate(symptom, 40)).toList(),
                    truncate(episode.rootCause(), properties.getMaxEpisodeTokens() / 2),
                    truncate(episode.fixContent(), properties.getMaxEpisodeTokens() / 2),
                    value(episode.reliability()),
                    episode.score(),
                    truncate(episode.matchReason(), 80),
                    value(episode.sourceUri())
            );
            int tokens = estimate(compressed);
            if (estimated + tokens > maxTokens) {
                continue;
            }
            episodes.add(compressed);
            estimated += tokens;
        }
        for (AgentMemoryNote note : context.agentNotes()) {
            if (estimated >= maxTokens) {
                break;
            }
            CompressedAgentNote compressed = new CompressedAgentNote(
                    value(note.agentName()),
                    value(note.phase()),
                    truncate(note.note(), properties.getMaxAgentNoteTokens()),
                    note.createdAt().toString()
            );
            int tokens = estimate(compressed);
            if (estimated + tokens > maxTokens) {
                continue;
            }
            notes.add(compressed);
            estimated += tokens;
        }
        boolean truncated = coreRules.size() < context.coreRules().size()
                || episodes.size() < context.recalledEpisodes().size()
                || notes.size() < context.agentNotes().size();
        return new CompressedMemoryContext(truncated ? "TOKEN_TRUNCATED" : "PASS",
                context.coreRules().size(), coreRules.size(),
                context.recalledEpisodes().size(), episodes.size(),
                context.agentNotes().size(), notes.size(),
                estimated, maxTokens, coreRules, episodes, notes);
    }

    private int estimate(Object value) {
        return TokenEstimator.estimate(JsonSupport.toJson(value), properties.getCharsPerToken());
    }

    private String truncate(String value, int maxTokens) {
        return TokenEstimator.truncateByTokens(value(value), Math.max(1, maxTokens), properties.getCharsPerToken());
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
