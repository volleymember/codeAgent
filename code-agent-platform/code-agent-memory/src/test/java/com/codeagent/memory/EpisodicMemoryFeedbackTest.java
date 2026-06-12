package com.codeagent.memory;

import com.codeagent.memory.config.MemoryProperties;
import com.codeagent.memory.model.MemoryFeedbackRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import com.codeagent.storage.repository.MemoryEpisodeRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpisodicMemoryFeedbackTest {
    @Test
    void helpfulFeedbackPromotesEpisodeConfidence() {
        MemoryEpisodeRepository repository = mock(MemoryEpisodeRepository.class);
        MemoryEpisodeEntity episode = new MemoryEpisodeEntity();
        episode.episodeId = "EP-1";
        episode.projectKey = "payment-service";
        episode.reliability = "candidate";
        episode.confidenceScore = 0.7;
        episode.helpfulCount = 2;
        episode.unhelpfulCount = 0;
        episode.updatedAt = LocalDateTime.now();
        when(repository.findByEpisodeId("EP-1")).thenReturn(Optional.of(episode));
        when(repository.save(any(MemoryEpisodeEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        EpisodicMemoryService service = new EpisodicMemoryService(repository,
                new EpisodicMemoryScorer(), new MemoryProperties());

        MemoryEpisodeEntity updated = service.feedback("EP-1",
                new MemoryFeedbackRequest(true, "FinalReportAgent", "same root cause confirmed"));

        assertThat(updated.helpfulCount).isEqualTo(3);
        assertThat(updated.confidenceScore).isGreaterThan(0.7);
        assertThat(updated.reliability).isEqualTo("verified");
        assertThat(updated.status).isEqualTo("ACTIVE");
        assertThat(updated.feedbackJson).contains("FinalReportAgent");
    }
}
