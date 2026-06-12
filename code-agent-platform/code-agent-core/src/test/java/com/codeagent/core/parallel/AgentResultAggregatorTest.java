package com.codeagent.core.parallel;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.common.enums.ToolCallStatus;
import com.codeagent.mcp.model.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResultAggregatorTest {
    @Test
    void deduplicatesEvidenceAndKeepsHighestScore() {
        AgentResultAggregator aggregator = new AgentResultAggregator();
        AgentWorkItem required = work("S1", true, AgentWorkType.TEST_EXECUTION);
        AgentWorkItem optional = work("S2", false, AgentWorkType.CODE_SEARCH);
        EvidenceItem lowScore = evidence("Jenkins test", 0.62);
        EvidenceItem highScore = evidence("Jenkins test", 0.91);

        ParallelAgentExecutionReport report = aggregator.aggregate("TASK-1", 2, 30, List.of(
                new AgentExecutionResult(required, new ToolCallResult("jenkins.get_test_report",
                        ToolCallStatus.SUCCESS.name(), "ok", null, List.of(lowScore), null, 12), 1,
                        true, false, 12, null),
                new AgentExecutionResult(optional, new ToolCallResult("rag.code_search",
                        ToolCallStatus.SUCCESS.name(), "ok", null, List.of(highScore), null, 18), 1,
                        true, false, 18, null)
        ));

        assertThat(report.successfulCount()).isEqualTo(2);
        assertThat(report.failedCount()).isZero();
        assertThat(report.evidence()).hasSize(1);
        assertThat(report.evidence().getFirst().score()).isEqualTo(0.91);
        assertThat(report.stats()).containsEntry("evidenceCount", 1);
    }

    private AgentWorkItem work(String stepId, boolean required, AgentWorkType workType) {
        return new AgentWorkItem("TASK-1", "SESSION-1", stepId, "TestAgent", AgentWorkSource.MCP_TOOL,
                workType, "tool." + stepId, Map.of(), null, required, 1, 1000, 0.8,
                "test", 100);
    }

    private EvidenceItem evidence(String title, double score) {
        return new EvidenceItem("jenkins_test_report", "JENKINS", title, "same failure", score,
                "https://jenkins.example.com/job/payment/15/testReport",
                "https://jenkins.example.com/job/payment/15/testReport",
                "N/A", "N/A", "raw://1", "same test failure", Map.of("case", "PaymentTest"));
    }
}
