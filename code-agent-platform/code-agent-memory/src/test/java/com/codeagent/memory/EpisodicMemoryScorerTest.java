package com.codeagent.memory;

import com.codeagent.common.json.JsonSupport;
import com.codeagent.memory.model.MemoryRecallRequest;
import com.codeagent.storage.entity.MemoryEpisodeEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpisodicMemoryScorerTest {
    private final EpisodicMemoryScorer scorer = new EpisodicMemoryScorer();

    @Test
    void givesHigherScoreForSimilarBugSymptoms() {
        MemoryRecallRequest request = new MemoryRecallRequest(
                "payment-service",
                "CI_FAILURE_ANALYSIS",
                "maven test failed NullPointerException in PaymentService",
                "session-1",
                List.of("NullPointerException", "PaymentService", "maven test failed"),
                5
        );
        MemoryEpisodeEntity similar = episode(List.of("PaymentService NullPointerException", "maven test failed"),
                "PaymentService did not mock account gateway.",
                "Add mock account gateway and cover null account branch.",
                "verified");
        MemoryEpisodeEntity unrelated = episode(List.of("frontend lint warning", "eslint no-unused-vars"),
                "Unused variable in React page.",
                "Remove unused import.",
                "verified");

        double similarScore = scorer.score(request, similar);
        double unrelatedScore = scorer.score(request, unrelated);

        assertThat(similarScore).isGreaterThan(unrelatedScore);
        assertThat(similarScore).isBetween(0.0, 1.0);
        assertThat(scorer.reason(request, similar, similarScore)).contains("matchedTerms");
    }

    private MemoryEpisodeEntity episode(List<String> symptoms, String rootCause, String fix, String reliability) {
        MemoryEpisodeEntity entity = new MemoryEpisodeEntity();
        entity.projectKey = "payment-service";
        entity.episodeId = "EP-test";
        entity.symptomsJson = JsonSupport.toJson(symptoms);
        entity.symptomSignature = "signature";
        entity.rootCause = rootCause;
        entity.fixContent = fix;
        entity.reliability = reliability;
        entity.confidenceScore = 0.8;
        return entity;
    }
}
