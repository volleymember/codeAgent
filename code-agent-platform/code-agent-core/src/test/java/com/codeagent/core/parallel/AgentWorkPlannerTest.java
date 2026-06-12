package com.codeagent.core.parallel;

import com.codeagent.core.agent.ToolPlan;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentWorkPlannerTest {
    @Test
    void buildsMcpAndRagWorkItemsForIndependentEvidenceCollection() {
        AgentProperties properties = new AgentProperties();
        properties.setEnableParallelCodeSearch(true);
        properties.setEnableParallelDocumentRetrieval(true);
        AgentWorkPlanner planner = new AgentWorkPlanner(properties);
        CreateAgentTaskCommand command = new CreateAgentTaskCommand(
                "CI_FAILURE_ANALYSIS",
                "payment-service",
                "https://gitlab.example.com/group/payment/-/merge_requests/8",
                "https://jenkins.example.com/job/payment/15",
                "payment-service",
                "PAY-1",
                "https://wiki.example.com/payment",
                "https://api.example.com/openapi.json"
        );

        List<AgentWorkItem> items = planner.plan("TASK-1", "SESSION-1", command, List.of(
                new ToolPlan("S1", "CIAnalysisAgent", "jenkins.get_test_report",
                        Map.of("jenkinsBuildUrl", command.jenkinsBuildUrl()), true, 0.9,
                        "test failure", 1400),
                new ToolPlan("S2", "GitAnalysisAgent", "gitlab.list_commits",
                        Map.of("gitlabMrUrl", command.gitlabMrUrl()), false, 0.7,
                        "git history", 900)
        ));

        assertThat(items).extracting(AgentWorkItem::workType)
                .contains(AgentWorkType.TEST_EXECUTION, AgentWorkType.GIT_HISTORY_ANALYSIS,
                        AgentWorkType.CODE_SEARCH, AgentWorkType.DOCUMENT_RETRIEVAL);
        assertThat(items).filteredOn(item -> item.source() == AgentWorkSource.RAG_SEARCH)
                .allSatisfy(item -> assertThat(item.ragSearchRequest()).isNotNull());
        assertThat(items).allSatisfy(item -> assertThat(item.maxAttempts()).isGreaterThanOrEqualTo(1));
    }
}
