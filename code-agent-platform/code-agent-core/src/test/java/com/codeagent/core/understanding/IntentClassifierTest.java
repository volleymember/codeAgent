package com.codeagent.core.understanding;

import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.llm.client.LlmClient;
import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.LlmResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {
    @Test
    void recognizesValidLeafNode() {
        IntentClassifier classifier = new IntentClassifier(new StubLlm("""
                {
                  "selectedIntentCode": "CI_FAILURE_ANALYSIS",
                  "confidence": 0.91,
                  "topCandidates": [
                    {"nodeCode": "CI_FAILURE_ANALYSIS", "confidence": 0.91, "matchedSignals": ["build failed"]}
                  ],
                  "matchedSignals": ["build failed"],
                  "ambiguity": false,
                  "extractedFacts": {"build": "15"}
                }
                """));

        IntentClassificationResult result = classifier.classify("TASK-1", "SESSION-1", "build failed",
                query(), leaves(), List.of(), "");

        assertThat(result.selectedIntentCode()).isEqualTo("CI_FAILURE_ANALYSIS");
        assertThat(result.confidence()).isEqualTo(0.91);
        assertThat(result.topCandidates()).hasSize(1);
        assertThat(result.extractedFacts()).containsEntry("build", "15");
    }

    @Test
    void fallsBackToUnknownWhenLlmInventsNodeCode() {
        IntentClassifier classifier = new IntentClassifier(new StubLlm("""
                {
                  "selectedIntentCode": "INVENTED_INTENT",
                  "confidence": 0.8,
                  "topCandidates": [
                    {"nodeCode": "INVENTED_INTENT", "confidence": 0.8, "matchedSignals": ["unknown"]}
                  ],
                  "matchedSignals": ["unknown"],
                  "ambiguity": true
                }
                """));

        IntentClassificationResult result = classifier.classify("TASK-1", "SESSION-1", "unknown",
                query(), leaves(), List.of(), "");

        assertThat(result.selectedIntentCode()).isEqualTo("UNKNOWN");
        assertThat(result.selectedIntentPath()).isEqualTo("UNKNOWN");
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("build failed", "build failed", List.of("build"),
                List.of("failure"), List.of("payment"), List.of("payment"), "", "", "", "",
                "", "", List.of(), 0.1);
    }

    private List<IntentLeafView> leaves() {
        return List.of(
                new IntentLeafView("default", 1, "CI_FAILURE_ANALYSIS", "ROOT/CI_FAILURE_ANALYSIS",
                        "CI", "CI", List.of("ci"), List.of(), 24, List.of("jenkins"), List.of("jenkins_log")),
                new IntentLeafView("default", 1, "UNKNOWN", "UNKNOWN", "Unknown", "Fallback",
                        List.of(), List.of(), 24, List.of(), List.of())
        );
    }

    static class StubLlm implements LlmClient {
        private final Queue<String> responses = new ArrayDeque<>();

        StubLlm(String... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public LlmResponse chat(LlmRequest request) {
            return new LlmResponse("REQ", "deepseek-v4-pro", responses.remove(), 0, 0, 1);
        }
    }
}
