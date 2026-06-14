package com.codeagent.core.react;

import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import com.codeagent.core.understanding.IntentClassificationResult;
import com.codeagent.core.understanding.ProjectContext;
import com.codeagent.core.understanding.QueryUnderstandingResult;
import com.codeagent.core.understanding.ResolvedTimeRange;
import com.codeagent.core.understanding.TimeRangeResolver;
import com.codeagent.mcp.model.ToolCallResult;
import com.codeagent.mcp.model.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallGuardrailTest {
    private final AgentProperties properties = new AgentProperties();
    private final ToolCallGuardrail guardrail = new ToolCallGuardrail(new TimeRangeResolver(properties));

    @Test
    void rejectsUnknownTool() {
        ToolCallValidation validation = guardrail.validate(new ToolPlanCandidate("missing.tool", "", Map.of(),
                List.of(), 1, ""), List.of(tool(false)), context(List.of(), range(1), List.of("jenkins")));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.rejectedReason()).isEqualTo("TOOL_NOT_FOUND");
    }

    @Test
    void rejectsMissingRequiredInputs() {
        ToolCallValidation validation = guardrail.validate(new ToolPlanCandidate("jenkins.get_test_report", "",
                Map.of(), List.of(), 1, ""), List.of(tool(false)), context(List.of(), range(1), List.of("jenkins")));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.rejectedReason()).contains("MISSING_REQUIRED_INPUTS");
    }

    @Test
    void rejectsHighCostToolWhenRangeTooLarge() {
        ToolCallValidation validation = guardrail.validate(new ToolPlanCandidate("jenkins.get_test_report", "",
                Map.of("jenkinsBuildUrl", "https://jenkins/job/1"), List.of(), 1, ""),
                List.of(tool(true)), context(List.of(), range(48), List.of("jenkins")));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.rejectedReason()).isEqualTo("HIGH_COST_TIME_RANGE_TOO_LARGE");
    }

    @Test
    void rejectsIntentToolTypeMismatch() {
        ToolCallValidation validation = guardrail.validate(new ToolPlanCandidate("jenkins.get_test_report", "",
                Map.of("jenkinsBuildUrl", "https://jenkins/job/1"), List.of(), 1, ""),
                List.of(tool(false)), context(List.of(), range(1), List.of("gitlab")));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.rejectedReason()).isEqualTo("TOOL_TYPE_NOT_ALLOWED_FOR_INTENT");
    }

    @Test
    void rejectsDuplicateSuccessfulToolCall() {
        ToolCallResult previous = new ToolCallResult("jenkins.get_test_report", "SUCCESS", "ok",
                "raw://1", List.of(), null, 1);
        ToolCallValidation validation = guardrail.validate(new ToolPlanCandidate("jenkins.get_test_report", "",
                Map.of("jenkinsBuildUrl", "https://jenkins/job/1"), List.of(), 1, ""),
                List.of(tool(false)), context(List.of(previous), range(1), List.of("jenkins"),
                List.of(guardrail.key("jenkins.get_test_report", resolvedDuplicateInput()))));

        assertThat(validation.accepted()).isFalse();
        assertThat(validation.rejectedReason()).isEqualTo("DUPLICATE_SUCCESSFUL_TOOL_CALL");
    }

    private ToolDefinition tool(boolean highCost) {
        return new ToolDefinition("jenkins.get_test_report", "Jenkins", "Fetch tests",
                List.of("jenkinsBuildUrl"), 1000, List.of("jenkins"), 500, highCost);
    }

    private Map<String, Object> resolvedDuplicateInput() {
        return Map.of(
                "jenkinsBuildUrl", "https://jenkins/job/1",
                "repoName", "repo",
                "gitlabProjectId", "1",
                "jenkinsJobName", "job",
                "sonarqubeProjectKey", "sonar",
                "serviceName", "payment",
                "apmServiceName", "apm",
                "logIndex", "logs",
                "branch", "main"
        );
    }

    private InvestigationContext context(List<ToolCallResult> previous,
                                         ResolvedTimeRange range,
                                         List<String> allowedToolTypes) {
        return context(previous, range, allowedToolTypes, List.of());
    }

    private InvestigationContext context(List<ToolCallResult> previous,
                                         ResolvedTimeRange range,
                                         List<String> allowedToolTypes,
                                         List<String> successfulKeys) {
        CreateAgentTaskCommand command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment",
                null, null, null, null, null, null);
        ProjectContext project = new ProjectContext("payment", "payment", "repo", "1",
                "job", "sonar", "main", "logs", "apm", true, List.of());
        IntentLeafView leaf = new IntentLeafView("default", 1, "CI_FAILURE_ANALYSIS", "ROOT/CI",
                "CI", "CI", List.of(), List.of(), 24, allowedToolTypes, List.of());
        return new InvestigationContext("TASK-1", "SESSION-1", command, query(),
                new IntentClassificationResult("CI_FAILURE_ANALYSIS", "ROOT/CI", 0.9, List.of(),
                        List.of(), false, "", Map.of()), leaf, range, project, null,
                Map.of(), List.of(), previous, List.of(), successfulKeys, List.of(), List.of(), "");
    }

    private ResolvedTimeRange range(int hours) {
        Instant end = Instant.parse("2026-06-12T00:00:00Z");
        return new ResolvedTimeRange(end.minusSeconds(hours * 3600L), end, hours, "TEST", List.of());
    }

    private QueryUnderstandingResult query() {
        return new QueryUnderstandingResult("q", "q", List.of(), List.of(), List.of(), List.of(),
                "", "", "", "", "", "", List.of(), 0.2);
    }
}
