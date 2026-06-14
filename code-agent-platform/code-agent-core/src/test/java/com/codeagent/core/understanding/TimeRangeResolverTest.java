package com.codeagent.core.understanding;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.core.config.AgentProperties;
import com.codeagent.core.dto.CreateAgentTaskCommand;
import com.codeagent.core.intent.dto.IntentLeafView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeRangeResolverTest {
    @Test
    void explicitStartAndEndWinOverQueryTimeExpression() {
        TimeRangeResolver resolver = new TimeRangeResolver(new AgentProperties());
        CreateAgentTaskCommand command = new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment",
                null, null, null, null, null, null, "recent 2h", null,
                "2026-06-12T00:00:00Z", "2026-06-12T03:00:00Z", null, null);

        ResolvedTimeRange range = resolver.resolve(command, query("最近 2 小时"), leaf("CI_FAILURE_ANALYSIS", 24));

        assertThat(range.source()).isEqualTo("EXPLICIT");
        assertThat(range.hours()).isEqualTo(3);
    }

    @Test
    void defaultsByIntentWhenNoTimeRangeProvided() {
        TimeRangeResolver resolver = new TimeRangeResolver(new AgentProperties());

        ResolvedTimeRange range = resolver.resolve(command("MR_IMPACT_ANALYSIS"), query(""),
                leaf("MR_IMPACT_ANALYSIS", null));

        assertThat(range.source()).isEqualTo("INTENT_DEFAULT");
        assertThat(range.hours()).isEqualTo(24 * 7);
    }

    @Test
    void highCostRangeLimitIsEnforced() {
        TimeRangeResolver resolver = new TimeRangeResolver(new AgentProperties());
        ResolvedTimeRange sevenDays = resolver.resolve(new CreateAgentTaskCommand("CI_FAILURE_ANALYSIS", "payment",
                null, null, null, null, null, null, null, null,
                "2026-06-05T00:00:00Z", "2026-06-12T00:00:00Z", null, null), query(""),
                leaf("CI_FAILURE_ANALYSIS", 24));

        assertThatThrownBy(() -> resolver.enforceHighCostRange(sevenDays, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("High cost tools");
    }

    private CreateAgentTaskCommand command(String taskType) {
        return new CreateAgentTaskCommand(taskType, "payment", null, null, null, null, null, null);
    }

    private QueryUnderstandingResult query(String timeExpression) {
        return new QueryUnderstandingResult("q", "q", List.of(), List.of(), List.of(), List.of(),
                "", timeExpression, "", "", "", "", List.of(), 0.2);
    }

    private IntentLeafView leaf(String code, Integer defaultHours) {
        return new IntentLeafView("default", 1, code, "ROOT/" + code, code, code,
                List.of(), List.of(), defaultHours, List.of(), List.of());
    }
}
