package com.codeagent.core.understanding;

import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntentAmbiguityResolverTest {
    private final LlmClient llm = request -> new LlmResponse("REQ", "deepseek-v4-pro",
            "{\"clarificationQuestion\":\"请确认排查 CI 失败还是线上故障？\"}", 0, 0, 1);
    private final IntentAmbiguityResolver resolver = new IntentAmbiguityResolver(llm);

    @Test
    void closeTopCandidatesNeedClarification() {
        AmbiguityDecision decision = resolver.resolve("TASK-1", "SESSION-1", command(), query(),
                new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.70,
                        List.of(new IntentCandidate("CI_FAILURE_ANALYSIS", 0.70, List.of()),
                                new IntentCandidate("PROD_INCIDENT_ANALYSIS", 0.60, List.of())),
                        List.of(), false, "", Map.of()));

        assertThat(decision.needsClarification()).isTrue();
        assertThat(decision.reason()).isEqualTo("CLOSE_INTENT_CANDIDATES");
        assertThat(decision.clarificationQuestion()).contains("CI");
    }

    @Test
    void lowConfidenceNeedsClarification() {
        AmbiguityDecision decision = resolver.resolve("TASK-1", "SESSION-1", command(), query(),
                new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.55,
                        List.of(new IntentCandidate("CI_FAILURE_ANALYSIS", 0.55, List.of())),
                        List.of(), false, "", Map.of()));

        assertThat(decision.needsClarification()).isTrue();
        assertThat(decision.reason()).isEqualTo("LOW_CONFIDENCE");
    }

    @Test
    void strongIntentDoesNotNeedClarification() {
        AmbiguityDecision decision = resolver.resolve("TASK-1", "SESSION-1", command(), query(),
                new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.90,
                        List.of(new IntentCandidate("CI_FAILURE_ANALYSIS", 0.90, List.of()),
                                new IntentCandidate("MR_IMPACT_ANALYSIS", 0.50, List.of())),
                        List.of(), false, "", Map.of()));

        assertThat(decision.needsClarification()).isFalse();
    }

    private CreateAgentTaskCommand command() {
        return new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment", null, null,
                null, null, null, null);
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("build failed", "build failed", List.of(), List.of(),
                List.of("payment"), List.of("payment"), "", "", "", "", "", "", List.of(), 0.2);
    }
}
