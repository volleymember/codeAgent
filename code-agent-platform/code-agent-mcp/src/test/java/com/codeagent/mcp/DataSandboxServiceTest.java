package com.codeagent.mcp;

import com.codeagent.common.domain.EvidenceItem;
import com.codeagent.mcp.config.DataSandboxProperties;
import com.codeagent.mcp.model.ToolDefinition;
import com.codeagent.mcp.sandbox.DataArtifactType;
import com.codeagent.mcp.sandbox.DataSandboxService;
import com.codeagent.mcp.sandbox.SandboxedToolPayload;
import com.codeagent.mcp.tool.ToolExecutionPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataSandboxServiceTest {
    @Test
    void extractsKeyLinesAndCompressesConsoleLog() {
        DataSandboxProperties properties = new DataSandboxProperties();
        properties.setMaxSummaryTokens(160);
        properties.setMaxEvidenceTokens(120);
        properties.setMaxKeyEvidence(8);
        DataSandboxService service = new DataSandboxService(properties);
        ToolDefinition definition = new ToolDefinition(
                "jenkins.get_console_log_summary",
                "Jenkins",
                "Fetch console log",
                List.of("jenkinsBuildUrl"),
                1000,
                List.of("jenkins", "ci", "log"),
                2600,
                true
        );
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            log.append("[INFO] compiling module ").append(i).append('\n');
        }
        log.append("[ERROR] COMPILATION FAILURE token=super-secret\n");
        log.append("java.lang.IllegalStateException: failed to resolve dependency\n");
        ToolExecutionPayload payload = new ToolExecutionPayload(
                Map.of("consoleLog", log.toString()),
                "Jenkins console log captured.",
                List.of(new EvidenceItem("jenkins_console_log_summary", "Console log", "full log", 0.84,
                        "https://jenkins.example.com/job/a/1/console", null, Map.of("buildId", "1")))
        );

        SandboxedToolPayload result = service.sandbox(definition, payload, "minio://code-agent/log.json");

        assertThat(result.artifactType()).isEqualTo(DataArtifactType.COMPILE_OUTPUT);
        assertThat(result.rawTokens()).isGreaterThan(result.contextTokens());
        assertThat(result.summary()).contains("COMPILATION FAILURE").doesNotContain("super-secret");
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().getFirst().metadata()).containsKey("sandbox");
        assertThat(result.compressionRatio()).isBetween(0.0, 1.0);
    }
}
